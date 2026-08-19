package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.ProcessedWebhookEvent;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.repository.ProcessedWebhookEventRepository;
import com.ecommerce.project.service.impl.StripeWebhookServiceImpl;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StripeWebhookServiceImplTest {

    @Mock private ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private StripeWebhookServiceImpl webhookService;

    private Event buildPaymentIntentEvent(String eventId, String type, PaymentIntent paymentIntent) {
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.ofNullable(paymentIntent));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    @Test
    void paymentIntentSucceeded_updatesPaymentAndSavesProcessedEvent() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        PaymentIntent paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getId()).thenReturn("pi_123");

        Payment payment = new Payment();
        payment.setPaymentId(1L);
        payment.setPgPaymentId("pi_123");
        payment.setPgStatus("pending");

        when(processedWebhookEventRepository.existsByEventId("evt_123")).thenReturn(false);
        when(paymentRepository.findByPgPaymentId("pi_123")).thenReturn(Optional.of(payment));

        Event event = buildPaymentIntentEvent("evt_123", "payment_intent.succeeded", paymentIntent);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        assertEquals("succeeded", payment.getPgStatus());
        verify(paymentRepository).save(payment);
        verify(processedWebhookEventRepository).save(argThat(p ->
                "evt_123".equals(p.getEventId()) && "payment_intent.succeeded".equals(p.getEventType())));
    }

    @Test
    void duplicateEvent_isIgnoredAndDoesNotUpdatePayment() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        PaymentIntent paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getId()).thenReturn("pi_123");

        when(processedWebhookEventRepository.existsByEventId("evt_dup")).thenReturn(true);

        Event event = buildPaymentIntentEvent("evt_dup", "payment_intent.succeeded", paymentIntent);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        verify(paymentRepository, never()).findByPgPaymentId(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(processedWebhookEventRepository, never()).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void paymentIntentFailed_updatesPaymentWithFailureMessage() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        PaymentIntent paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getId()).thenReturn("pi_456");

        StripeError error = mock(StripeError.class);
        when(error.getMessage()).thenReturn("Card declined");
        when(paymentIntent.getLastPaymentError()).thenReturn(error);

        Payment payment = new Payment();
        payment.setPaymentId(2L);
        payment.setPgPaymentId("pi_456");

        when(processedWebhookEventRepository.existsByEventId("evt_failed")).thenReturn(false);
        when(paymentRepository.findByPgPaymentId("pi_456")).thenReturn(Optional.of(payment));

        Event event = buildPaymentIntentEvent("evt_failed", "payment_intent.payment_failed", paymentIntent);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        assertEquals("failed", payment.getPgStatus());
        assertEquals("Card declined", payment.getPgResponseMessage());
    }

    @Test
    void missingSignature_throwsException() {
        assertThrows(APIException.class, () -> webhookService.handleWebhook("payload", ""));
        assertThrows(APIException.class, () -> webhookService.handleWebhook("payload", null));
    }
}
