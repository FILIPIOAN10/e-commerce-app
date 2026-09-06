package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.Refund;
import com.ecommerce.project.model.RefundStatus;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.repository.RefundRepository;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.RefundOutboxPayload;
import com.stripe.model.Charge;
import com.stripe.model.RefundCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundServiceImpl")
class RefundServiceImplTest {

    @Mock private RefundRepository refundRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    private RefundServiceImpl service;

    private ReturnRequest returnRequest;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new RefundServiceImpl(refundRepository, paymentRepository, outboxEventPublisher);
        ReflectionTestUtils.setField(service, "enabled", true);

        returnRequest = new ReturnRequest();
        returnRequest.setId(7L);
        returnRequest.setOrderId(3L);
        returnRequest.setRefundAmount(new BigDecimal("84.99"));

        order = new Order();
        order.setId(3L);
        order.setTotalAmount(new BigDecimal("84.99"));
    }

    private void cardPayment() {
        Payment p = new Payment();
        p.setPgName("Stripe");
        p.setPgPaymentId("pi_abc");
        order.setPayment(p);
    }

    @Test
    @DisplayName("a card order records a PENDING refund and enqueues the Stripe call")
    void cardOrderQueuesRefund() {
        cardPayment();
        when(refundRepository.existsByReturnId(7L)).thenReturn(false);
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });

        service.requestRefundForReturn(returnRequest, order);

        verify(refundRepository).save(any(Refund.class));
        verify(outboxEventPublisher).publish(eq(OutboxEventTypes.REFUND_REQUESTED), eq(new RefundOutboxPayload(99L)));
    }

    @Test
    @DisplayName("a cash-on-delivery order is left for a manual refund — nothing queued")
    void codOrderDoesNothing() {
        Payment cod = new Payment();
        cod.setPgName("COD");
        cod.setPgPaymentId(null);
        order.setPayment(cod);

        service.requestRefundForReturn(returnRequest, order);

        verifyNoInteractions(refundRepository);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("a return whose refund was already requested is rejected before any Stripe work")
    void duplicateRequestRejected() {
        cardPayment();
        when(refundRepository.existsByReturnId(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.requestRefundForReturn(returnRequest, order))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("already been requested");

        verify(refundRepository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("with automated refunds disabled the service is a no-op")
    void disabledIsNoOp() {
        ReflectionTestUtils.setField(service, "enabled", false);
        cardPayment();

        service.requestRefundForReturn(returnRequest, order);

        verifyNoInteractions(refundRepository);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("charge.refunded reconciliation adopts the Stripe id onto the in-flight refund")
    void reconcileAdoptsStripeId() {
        Refund inFlight = Refund.pendingFor(7L, 3L, "pi_abc", new BigDecimal("84.99"));
        inFlight.setId(99L);

        com.stripe.model.Refund stripeRefund = mock(com.stripe.model.Refund.class);
        when(stripeRefund.getId()).thenReturn("re_xyz");
        when(stripeRefund.getAmount()).thenReturn(8499L);
        RefundCollection refunds = mock(RefundCollection.class);
        when(refunds.getData()).thenReturn(List.of(stripeRefund));
        Charge charge = mock(Charge.class);
        when(charge.getPaymentIntent()).thenReturn("pi_abc");
        when(charge.getRefunds()).thenReturn(refunds);

        when(refundRepository.findByStripeRefundId("re_xyz")).thenReturn(Optional.empty());
        when(refundRepository.findByPaymentIntentIdAndStatus("pi_abc", RefundStatus.PENDING))
                .thenReturn(List.of(inFlight));

        service.reconcileFromCharge(charge);

        assertThat(inFlight.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(inFlight.getStripeRefundId()).isEqualTo("re_xyz");
        verify(refundRepository).save(inFlight);
    }
}
