package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.ProcessedWebhookEvent;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.repository.ProcessedWebhookEventRepository;
import com.ecommerce.project.service.StripeWebhookService;
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
        }, () -> log.warn("No local payment found for Stripe payment intent {}", paymentIntentId));
    }
}
