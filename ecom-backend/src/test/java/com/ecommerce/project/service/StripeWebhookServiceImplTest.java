package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.ProcessedWebhookEvent;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.repository.ProcessedWebhookEventRepository;
import com.ecommerce.project.service.impl.StripeWebhookServiceImpl;
import com.ecommerce.project.service.order.OrderStatus;
import com.ecommerce.project.service.subscription.SubscriptionEventDispatcher;
import com.stripe.model.Charge;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    @Mock private OrderService orderService;
    @Mock private RefundService refundService;
    @Mock private com.ecommerce.project.service.DisputeService disputeService;
    @Mock private SubscriptionEventDispatcher subscriptionEventDispatcher;

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

        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByPgPaymentId("pi_123")).thenReturn(Optional.of(payment));

        Event event = buildPaymentIntentEvent("evt_123", "payment_intent.succeeded", paymentIntent);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        assertEquals("succeeded", payment.getPgStatus());
        verify(paymentRepository).save(payment);
        verify(processedWebhookEventRepository).saveAndFlush(argThat(p ->
                "evt_123".equals(p.getEventId()) && "payment_intent.succeeded".equals(p.getEventType())));
    }

    @Test
    void duplicateEvent_isIgnoredAndDoesNotUpdatePayment() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        PaymentIntent paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getId()).thenReturn("pi_123");

        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        Event event = buildPaymentIntentEvent("evt_dup", "payment_intent.succeeded", paymentIntent);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        verify(paymentRepository, never()).findByPgPaymentId(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
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

        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
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

    private Event buildChargeEvent(String eventId, String type, Charge charge) {
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.ofNullable(charge));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    @Test
    void chargeRefunded_transitionsOrderToRefunded() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        Charge charge = mock(Charge.class);
        when(charge.getPaymentIntent()).thenReturn("pi_refund");

        Order order = new Order();
        order.setId(77L);
        order.setOrderStatus(OrderStatus.DELIVERED);

        Payment payment = new Payment();
        payment.setPaymentId(3L);
        payment.setPgPaymentId("pi_refund");
        payment.setOrder(order);

        when(processedWebhookEventRepository.existsByEventId("evt_refund")).thenReturn(false);
        when(paymentRepository.findByPgPaymentId("pi_refund")).thenReturn(Optional.of(payment));

        Event event = buildChargeEvent("evt_refund", "charge.refunded", charge);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        assertEquals("refunded", payment.getPgStatus());
        verify(orderService).updateOrder(77L, OrderStatus.REFUNDED);
    }

    @Test
    void chargeRefunded_withoutOrder_doesNotTouchOrderService() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        Charge charge = mock(Charge.class);
        when(charge.getPaymentIntent()).thenReturn("pi_orphan");

        Payment payment = new Payment();
        payment.setPaymentId(4L);
        payment.setPgPaymentId("pi_orphan");

        when(processedWebhookEventRepository.existsByEventId("evt_orphan")).thenReturn(false);
        when(paymentRepository.findByPgPaymentId("pi_orphan")).thenReturn(Optional.of(payment));

        Event event = buildChargeEvent("evt_orphan", "charge.refunded", charge);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        assertEquals("refunded", payment.getPgStatus());
        verify(orderService, never()).updateOrder(anyLong(), anyString());
    }

    @Test
    void chargeDisputeCreated_isRoutedToTheDisputeService() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        com.stripe.model.Dispute dispute = mock(com.stripe.model.Dispute.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(dispute));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_dispute");
        when(event.getType()).thenReturn("charge.dispute.created");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        verify(disputeService).openFromStripe(dispute);
        verify(subscriptionEventDispatcher, never()).dispatch(any());
    }

    @Test
    void paymentIntentFailed_cancelsAssociatedOrder() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        PaymentIntent paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getId()).thenReturn("pi_fail_order");

        Order order = new Order();
        order.setId(88L);
        order.setOrderStatus(OrderStatus.PLACED);

        Payment payment = new Payment();
        payment.setPaymentId(5L);
        payment.setPgPaymentId("pi_fail_order");
        payment.setOrder(order);

        when(processedWebhookEventRepository.existsByEventId("evt_fail_order")).thenReturn(false);
        when(paymentRepository.findByPgPaymentId("pi_fail_order")).thenReturn(Optional.of(payment));

        Event event = buildPaymentIntentEvent("evt_fail_order", "payment_intent.payment_failed", paymentIntent);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        verify(orderService).updateOrder(88L, OrderStatus.CANCELLED);
    }

    @Test
    void unhandledEventType_isRoutedToSubscriptionDispatcher() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_test");

        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Event event = buildChargeEvent("evt_sub", "customer.subscription.updated", mock(Charge.class));

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_test"))).thenReturn(event);
            webhookService.handleWebhook("payload", "sig");
        }

        verify(subscriptionEventDispatcher).dispatch(event);
        verify(paymentRepository, never()).findByPgPaymentId(anyString());
    }
}
