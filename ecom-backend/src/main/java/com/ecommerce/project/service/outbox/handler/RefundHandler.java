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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
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
 *
 * <p><strong>Transaction boundary.</strong> The method used to be wrapped in a
 * single {@code @Transactional}, which parked a Hikari connection for the whole
 * Stripe HTTP call — 80s of read timeout, times every in-flight refund. It now
 * runs in three phases: a short read-only transaction to load the pending row,
 * the Stripe call with no transaction open, then another short transaction to
 * write the outcome. {@link TransactionTemplate} is used instead of
 * self-invoked {@code @Transactional} methods because Spring's AOP proxy does
 * not intercept a {@code this.foo()} call — annotating a helper on the same
 * bean would silently run it with no transaction at all.
 */
@Slf4j
@Component
public class RefundHandler implements OutboxHandler {

    private static final Set<String> TERMINAL_STRIPE_STATUSES = Set.of("failed", "canceled");

    private final RefundRepository refundRepository;
    private final StripeService stripeService;
    private final NotificationService notificationService;
    private final OutboxPayloadCodec payloadCodec;
    private final TransactionTemplate readOnlyTx;
    private final TransactionTemplate writeTx;

    @Autowired
    public RefundHandler(RefundRepository refundRepository,
                         StripeService stripeService,
                         NotificationService notificationService,
                         OutboxPayloadCodec payloadCodec,
                         PlatformTransactionManager txManager) {
        this.refundRepository = refundRepository;
        this.stripeService = stripeService;
        this.notificationService = notificationService;
        this.payloadCodec = payloadCodec;
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    @Override
    public String eventType() {
        return OutboxEventTypes.REFUND_REQUESTED;
    }

    @Override
    public void handle(String payload) {
        RefundOutboxPayload data = payloadCodec.deserialize(payload, RefundOutboxPayload.class);

        RefundSnapshot snapshot = readOnlyTx.execute(status -> loadPending(data.refundId()));
        if (snapshot == null) {
            return; // a prior delivery, or the webhook, already settled this one
        }

        // Stripe call runs OUTSIDE any transaction: the DB connection borrowed
        // for the read above has already been returned to the pool, and the
        // one for the write below will not be borrowed until Stripe answers.
        try {
            RefundResult result = stripeService.issueRefund(
                    snapshot.paymentIntentId(), snapshot.minorUnits(), "refund:" + snapshot.id());

            if (TERMINAL_STRIPE_STATUSES.contains(result.status())) {
                writeTx.executeWithoutResult(status ->
                        fail(snapshot.id(), "Stripe reported refund status: " + result.status()));
                return;
            }
            writeTx.executeWithoutResult(status -> succeed(snapshot.id(), result.stripeRefundId()));

        } catch (InvalidRequestException e) {
            // Already refunded elsewhere, or the charge is not refundable — no
            // amount of retrying fixes this, and the customer may already have
            // their money. Surface it to a human rather than loop.
            writeTx.executeWithoutResult(status ->
                    fail(snapshot.id(), "Stripe rejected the refund: " + e.getMessage()
                            + " — check the payment in Stripe; the customer may already have been refunded."));

        } catch (StripeException e) {
            throw new IllegalStateException(
                    "Stripe refund call failed for refund " + snapshot.id() + " — will retry", e);
        }
    }

    private RefundSnapshot loadPending(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalStateException("Refund " + refundId + " no longer exists"));
        if (refund.getStatus() != RefundStatus.PENDING) {
            return null;
        }
        return new RefundSnapshot(
                refund.getId(),
                refund.getOrderId(),
                refund.getPaymentIntentId(),
                refund.getAmount(),
                Money.of(refund.getAmount()).toCents());
    }

    private void succeed(Long refundId, String stripeRefundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalStateException("Refund " + refundId + " vanished mid-flight"));
        if (refund.getStatus() != RefundStatus.PENDING) {
            // A concurrent webhook has already settled it. Stripe's
            // idempotency key means our call returned the same refund, so
            // dropping our write here loses nothing.
            return;
        }
        refund.markSucceeded(stripeRefundId);
        refundRepository.save(refund);
        log.info("Refund {} succeeded: {} refunded, stripe id {}",
                refundId, refund.getAmount(), stripeRefundId);
    }

    private void fail(Long refundId, String reason) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalStateException("Refund " + refundId + " vanished mid-flight"));
        if (refund.getStatus() != RefundStatus.PENDING) {
            return;
        }
        refund.markFailed(reason);
        refundRepository.save(refund);
        log.error("Refund {} failed permanently: {}", refundId, reason);
        notificationService.notifyAdminRefundFailed(refund.getOrderId(), refund.getAmount(), reason);
    }

    /** Fields read from the {@link Refund} row before the Stripe call. */
    private record RefundSnapshot(
            Long id,
            Long orderId,
            String paymentIntentId,
            BigDecimal amount,
            long minorUnits) {}
}
