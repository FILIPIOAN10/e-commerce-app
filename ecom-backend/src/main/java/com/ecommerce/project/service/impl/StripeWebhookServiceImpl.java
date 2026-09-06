package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.ProcessedWebhookEvent;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.repository.ProcessedWebhookEventRepository;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.RefundService;
import com.ecommerce.project.service.StripeWebhookService;
import com.ecommerce.project.service.payment.PaymentStatus;
import com.ecommerce.project.service.subscription.SubscriptionEventDispatcher;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookServiceImpl implements StripeWebhookService {

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final RefundService refundService;
    private final SubscriptionEventDispatcher subscriptionEventDispatcher;

    @Override
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new APIException("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new APIException("Invalid Stripe webhook signature: " + e.getMessage());
        } catch (Exception e) {
            throw new APIException("Failed to parse Stripe webhook: " + e.getMessage());
        }

        String eventId = event.getId();

        // Insert the dedup record BEFORE processing. The unique constraint on
        // event_id makes this atomic — if two concurrent webhooks carry the
        // same event, only one insert succeeds; the other gets a
        // DataIntegrityViolationException and skips processing.
        // The previous check-then-process-then-save pattern allowed both
        // threads to pass the existsByEventId check and double-process.
        ProcessedWebhookEvent record = new ProcessedWebhookEvent();
        record.setEventId(eventId);
        record.setEventType(event.getType());
        record.setPayload(payload);
        record.setProcessedAt(Instant.now());
        try {
            processedWebhookEventRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            log.info("Stripe event {} already processed by a concurrent request; skipping", eventId);
            return;
        }

        processEvent(event);

        log.info("Stripe event {} processed successfully", eventId);
    }

    private void processEvent(Event event) {
        String eventType = event.getType();

        switch (eventType) {
            case "payment_intent.succeeded" -> onPaymentIntent(event, intent ->
                    applyPaymentStatus(intent.getId(), PaymentStatus.SUCCEEDED, "Payment succeeded", eventType));

            case "payment_intent.payment_failed" -> onPaymentIntent(event, intent -> {
                String failureMessage = intent.getLastPaymentError() != null
                        ? intent.getLastPaymentError().getMessage()
                        : "Payment failed";
                applyPaymentStatus(intent.getId(), PaymentStatus.FAILED, failureMessage, eventType);
            });

            // A refund can be partial or full. In both cases we move the order to
            // Refunded so the inventory and financial state stay consistent with
            // Stripe, and reconcile the local refunds record — whether we issued
            // the refund ourselves (the outbox path) or it was done in the Stripe
            // dashboard, the two converge on one row.
            case "charge.refunded" -> onCharge(event, charge -> {
                applyPaymentStatus(charge.getPaymentIntent(), PaymentStatus.REFUNDED, "Payment refunded", eventType);
                refundService.reconcileFromCharge(charge);
            });

            // Subscription lifecycle (checkout completed, renewal paid/failed,
            // updated, deleted) is routed through its own Strategy registry.
            default -> subscriptionEventDispatcher.dispatch(event);
        }
    }

    private void onPaymentIntent(Event event, Consumer<PaymentIntent> handler) {
        event.getDataObjectDeserializer().getObject()
                .filter(PaymentIntent.class::isInstance)
                .map(PaymentIntent.class::cast)
                .ifPresent(handler);
    }

    private void onCharge(Event event, Consumer<Charge> handler) {
        event.getDataObjectDeserializer().getObject()
                .filter(Charge.class::isInstance)
                .map(Charge.class::cast)
                .ifPresent(handler);
    }

    /**
     * Single write path for every Stripe status transition.
     * <p>
     * Previously there were two near-identical methods and only one of them
     * handled refunds, which is exactly where the "refund never reaches the
     * order" bug hid. Keeping one path means a new payment status only has to
     * be mapped in {@link PaymentStatus} to be handled end to end.
     */
    private void applyPaymentStatus(String paymentIntentId, PaymentStatus status,
                                    String responseMessage, String eventType) {
        paymentRepository.findByPgPaymentId(paymentIntentId).ifPresentOrElse(payment -> {
            payment.setPgStatus(status.value());
            payment.setPgResponseMessage(responseMessage);
            paymentRepository.save(payment);
            log.info("Updated payment {} for Stripe intent {} to {} from event {}",
                    payment.getPaymentId(), paymentIntentId, status.value(), eventType);

            // Webhooks are authoritative for the payment outcome, so they drive the order.
            status.requiredOrderStatus().ifPresent(orderStatus ->
                    transitionOrder(payment, orderStatus));
        }, () -> log.warn("No local payment found for Stripe payment intent {}", paymentIntentId));
    }

    private void transitionOrder(Payment payment, String targetStatus) {
        Order order = payment.getOrder();
        if (order == null) {
            log.warn("Payment {} has no associated order; cannot move it to {}",
                    payment.getPaymentId(), targetStatus);
            return;
        }

        try {
            orderService.updateOrder(order.getId(), targetStatus);
            log.info("Moved order {} to {} from payment {}",
                    order.getId(), targetStatus, payment.getPaymentId());
        } catch (Exception e) {
            // An illegal transition (e.g. a second partial-refund webhook for an
            // already refunded order) must not fail the webhook: returning a non-2xx
            // would make Stripe retry forever.
            log.warn("Could not move order {} to {}: {}", order.getId(), targetStatus, e.getMessage());
        }
    }
}
