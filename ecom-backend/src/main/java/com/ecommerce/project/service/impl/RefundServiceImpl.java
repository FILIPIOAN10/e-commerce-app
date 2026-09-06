package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.Refund;
import com.ecommerce.project.model.RefundStatus;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.repository.RefundRepository;
import com.ecommerce.project.service.RefundService;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.RefundOutboxPayload;
import com.stripe.model.Charge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Owns the {@code refunds} record and the two paths that write it: the outbox
 * side effect that calls Stripe (enqueued here, run by {@code RefundHandler}) and
 * the {@code charge.refunded} webhook reconciliation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    /** {@code payment.pg_name} values that mean a Stripe card charge we can refund via the API. */
    private static final Set<String> STRIPE_PG_NAMES = Set.of("Stripe", "stripe", "STRIPE");

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Value("${app.refunds.enabled:true}")
    private boolean enabled;

    @Override
    public void requestRefundForReturn(ReturnRequest returnRequest, Order order) {
        if (!enabled) {
            log.info("Automated refunds disabled — return {} left for a manual refund", returnRequest.getId());
            return;
        }

        Payment payment = order.getPayment();
        String paymentIntentId = payment != null ? payment.getPgPaymentId() : null;
        boolean card = payment != null
                && paymentIntentId != null && !paymentIntentId.isBlank()
                && STRIPE_PG_NAMES.contains(String.valueOf(payment.getPgName()));
        if (!card) {
            log.info("Return {} settles a non-card payment ({}) — no Stripe refund to issue",
                    returnRequest.getId(), payment != null ? payment.getPgName() : "none");
            return;
        }

        // The unique index uk_refunds_return is the physical guarantee; this
        // check is what keeps the common cases (double-click, the tracking sweep
        // firing twice, a retried request) from poisoning the caller's
        // transaction with a duplicate-key violation at commit.
        if (refundRepository.existsByReturnId(returnRequest.getId())) {
            throw new APIException("A refund for this return has already been requested");
        }

        BigDecimal amount = returnRequest.getRefundAmount() != null
                ? returnRequest.getRefundAmount()
                : order.getTotalAmount();

        Refund refund = refundRepository.save(
                Refund.pendingFor(returnRequest.getId(), order.getId(), paymentIntentId, amount));

        outboxEventPublisher.publish(OutboxEventTypes.REFUND_REQUESTED, new RefundOutboxPayload(refund.getId()));
        log.info("Refund {} ({}) queued for return {}", refund.getId(), amount, returnRequest.getId());
    }

    @Override
    public void reconcileFromCharge(Charge charge) {
        if (charge.getRefunds() == null || charge.getRefunds().getData() == null
                || charge.getRefunds().getData().isEmpty()) {
            // The event did not expand the refund list — nothing to reconcile
            // here. The outbox handler is the authoritative writer of the id.
            return;
        }

        String paymentIntentId = charge.getPaymentIntent();
        for (com.stripe.model.Refund stripeRefund : charge.getRefunds().getData()) {
            if (refundRepository.findByStripeRefundId(stripeRefund.getId()).isPresent()) {
                continue; // already recorded by one of the two paths
            }

            BigDecimal amount = BigDecimal.valueOf(stripeRefund.getAmount()).movePointLeft(2);
            Refund refund = refundRepository
                    .findByPaymentIntentIdAndStatus(paymentIntentId, RefundStatus.PENDING).stream()
                    .findFirst()
                    // No in-flight refund: this was done straight in the Stripe
                    // dashboard. Record it so the two paths still converge.
                    .orElseGet(() -> Refund.pendingFor(
                            null,
                            paymentRepository.findByPgPaymentId(paymentIntentId)
                                    .map(p -> p.getOrder() != null ? p.getOrder().getId() : null)
                                    .orElse(null),
                            paymentIntentId,
                            amount));

            refund.markSucceeded(stripeRefund.getId());
            try {
                refundRepository.save(refund);
                log.info("Reconciled refund {} from charge.refunded (stripe id {})",
                        refund.getId(), stripeRefund.getId());
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // uk_refunds_stripe_id — the outbox handler wrote the id first. Fine.
                log.debug("Refund with stripe id {} already recorded by the outbox path", stripeRefund.getId());
            }
        }
    }
}
