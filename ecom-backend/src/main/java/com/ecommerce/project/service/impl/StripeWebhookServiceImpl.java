package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.ProcessedWebhookEvent;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.repository.ProcessedWebhookEventRepository;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.StripeWebhookService;
import com.ecommerce.project.service.order.OrderStatus;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookServiceImpl implements StripeWebhookService {

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

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
        if (processedWebhookEventRepository.existsByEventId(eventId)) {
            log.info("Stripe event {} already processed; treating as idempotent no-op", eventId);
            return;
        }

        processEvent(event);

        ProcessedWebhookEvent record = new ProcessedWebhookEvent();
        record.setEventId(eventId);
        record.setEventType(event.getType());
        record.setPayload(payload);
        record.setProcessedAt(Instant.now());
        processedWebhookEventRepository.save(record);

        log.info("Stripe event {} processed successfully", eventId);
    }

    private void processEvent(Event event) {
        String eventType = event.getType();

        if ("payment_intent.succeeded".equals(eventType) || "payment_intent.payment_failed".equals(eventType)) {
            event.getDataObjectDeserializer().getObject()
                    .filter(PaymentIntent.class::isInstance)
                    .map(PaymentIntent.class::cast)
                    .ifPresent(paymentIntent -> {
                        if ("payment_intent.succeeded".equals(eventType)) {
                            updatePayment(paymentIntent.getId(), "succeeded", "Payment succeeded", eventType);
                        } else {
                            String failureMessage = paymentIntent.getLastPaymentError() != null
                                    ? paymentIntent.getLastPaymentError().getMessage()
                                    : "Payment failed";
                            updatePayment(paymentIntent.getId(), "failed", failureMessage, eventType);
                        }
                    });
        } else if ("charge.refunded".equals(eventType)) {
            // Refund can be partial or full. In both cases we move the order to REFUNDED
            // so the inventory and financial state are consistent with Stripe.
            event.getDataObjectDeserializer().getObject()
                    .filter(com.stripe.model.Charge.class::isInstance)
                    .map(com.stripe.model.Charge.class::cast)
                    .ifPresent(charge -> updatePaymentAndOrder(
                            charge.getPaymentIntent(), "refunded", "Payment refunded", eventType));
        }

        // Additional event types can be handled here as the product evolves.
    }

    private void updatePayment(String paymentIntentId, String status, String responseMessage, String eventType) {
        paymentRepository.findByPgPaymentId(paymentIntentId).ifPresentOrElse(payment -> {
            payment.setPgStatus(status);
            payment.setPgResponseMessage(responseMessage);
            paymentRepository.save(payment);
            log.info("Updated payment {} for Stripe intent {} to {} from event {}",
                    payment.getPaymentId(), paymentIntentId, status, eventType);

            // Webhooks are authoritative: a failed payment cancels the order and restocks.
            if ("failed".equals(status)) {
                cancelOrderFromPayment(payment);
            }
        }, () -> log.warn("No local payment found for Stripe payment intent {}", paymentIntentId));
    }

    private void updatePaymentAndOrder(String paymentIntentId, String status, String responseMessage, String eventType) {
        paymentRepository.findByPgPaymentId(paymentIntentId).ifPresentOrElse(payment -> {
            payment.setPgStatus(status);
            payment.setPgResponseMessage(responseMessage);
            paymentRepository.save(payment);
            log.info("Updated payment {} for Stripe intent {} to {} from event {}",
                    payment.getPaymentId(), paymentIntentId, status, eventType);

            Order order = payment.getOrder();
            if (order != null) {
                if (OrderStatus.REFUNDED.equals(status)) {
                    orderService.updateOrder(order.getId(), OrderStatus.REFUNDED);
                } else if ("failed".equals(status)) {
                    cancelOrderFromPayment(payment);
                }
            }
        }, () -> log.warn("No local payment found for Stripe payment intent {}", paymentIntentId));
    }

    private void cancelOrderFromPayment(Payment payment) {
        Order order = payment.getOrder();
        if (order == null) {
            log.warn("Payment {} has no associated order; nothing to cancel", payment.getPaymentId());
            return;
        }

        try {
            orderService.updateOrder(order.getId(), OrderStatus.CANCELLED);
            log.info("Cancelled order {} due to failed payment {}", order.getId(), payment.getPaymentId());
        } catch (Exception e) {
            log.warn("Could not cancel order {}: {}", order.getId(), e.getMessage());
        }
    }
}
