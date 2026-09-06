package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.model.Refund;
import com.ecommerce.project.model.RefundStatus;
import com.ecommerce.project.repository.RefundRepository;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.RefundOutboxPayload;
import com.ecommerce.project.service.payment.RefundResult;
import com.ecommerce.project.service.pricing.Money;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Issues the Stripe refund for a {@code REFUND_REQUESTED} event.
 *
 * <p>Idempotent on three levels, which is what a durable, at-least-once queue
 * demands: the {@code Refund} row is only acted on while {@code PENDING}, so a
 * redelivery after success is a no-op; the Stripe call carries an
 * {@code Idempotency-Key} of {@code refund:{id}}, so a redelivery <em>before</em>
 * the status write returns the original refund rather than a second one; and
 * {@code uk_refunds_stripe_id} rejects a second row for the same Stripe refund.
 *
 * <p>A transient Stripe failure is rethrown so the dispatcher backs off and
 * retries. A permanent rejection (already refunded, not refundable) marks the
 * row {@code FAILED}, notifies an admin, and returns — retrying would not help.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundHandler implements OutboxHandler {

    private static final Set<String> TERMINAL_STRIPE_STATUSES = Set.of("failed", "canceled");

    private final RefundRepository refundRepository;
    private final StripeService stripeService;
    private final NotificationService notificationService;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.REFUND_REQUESTED;
    }

    @Override
    @Transactional
    public void handle(String payload) {
        RefundOutboxPayload data = payloadCodec.deserialize(payload, RefundOutboxPayload.class);
        Refund refund = refundRepository.findById(data.refundId())
                .orElseThrow(() -> new IllegalStateException("Refund " + data.refundId() + " no longer exists"));

        if (refund.getStatus() != RefundStatus.PENDING) {
            return; // a prior delivery, or the webhook, already settled this one
        }

        long minorUnits = Money.of(refund.getAmount()).toCents();
        try {
            RefundResult result = stripeService.issueRefund(
                    refund.getPaymentIntentId(), minorUnits, "refund:" + refund.getId());

            if (TERMINAL_STRIPE_STATUSES.contains(result.status())) {
                fail(refund, "Stripe reported refund status: " + result.status());
                return;
            }
            refund.markSucceeded(result.stripeRefundId());
            refundRepository.save(refund);
            log.info("Refund {} succeeded: {} refunded, stripe id {}",
                    refund.getId(), refund.getAmount(), result.stripeRefundId());

        } catch (InvalidRequestException e) {
            // Already refunded elsewhere, or the charge is not refundable — no
            // amount of retrying fixes this, and the customer may already have
            // their money. Surface it to a human rather than loop.
            fail(refund, "Stripe rejected the refund: " + e.getMessage()
                    + " — check the payment in Stripe; the customer may already have been refunded.");

        } catch (StripeException e) {
            throw new IllegalStateException(
                    "Stripe refund call failed for refund " + refund.getId() + " — will retry", e);
        }
    }

    private void fail(Refund refund, String reason) {
        refund.markFailed(reason);
        refundRepository.save(refund);
        log.error("Refund {} failed permanently: {}", refund.getId(), reason);
        notificationService.notifyAdminRefundFailed(refund.getOrderId(), refund.getAmount(), reason);
    }
}
