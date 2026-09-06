package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.Dispute;
import com.ecommerce.project.model.DisputeStatus;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.repository.DisputeEvidenceFileRepository;
import com.ecommerce.project.repository.DisputeRepository;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeServiceImplTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEvidenceFileRepository evidenceFileRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks private DisputeServiceImpl disputeService;

    private com.stripe.model.Dispute stripeDispute;
    private com.stripe.model.Dispute.EvidenceDetails evidenceDetails;

    @BeforeEach
    void setUp() {
        stripeDispute = org.mockito.Mockito.mock(com.stripe.model.Dispute.class);
        evidenceDetails = org.mockito.Mockito.mock(com.stripe.model.Dispute.EvidenceDetails.class);
        when(stripeDispute.getId()).thenReturn("dp_123");
        when(stripeDispute.getPaymentIntent()).thenReturn("pi_123");
        when(stripeDispute.getCharge()).thenReturn("ch_123");
        when(stripeDispute.getAmount()).thenReturn(8499L);
        when(stripeDispute.getCurrency()).thenReturn("usd");
        when(stripeDispute.getReason()).thenReturn("fraudulent");
        when(stripeDispute.getStatus()).thenReturn("needs_response");
        when(stripeDispute.getEvidenceDetails()).thenReturn(evidenceDetails);
        when(evidenceDetails.getDueBy()).thenReturn(1_800_000_000L);
        when(evidenceDetails.getSubmissionCount()).thenReturn(0L);

        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(1L);
            }
            return d;
        });
    }

    private Dispute captureSaved() {
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        verify(disputeRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void openFromStripe_recordsANewDisputeAndResolvesTheOrder() {
        Order order = new Order();
        order.setId(55L);
        Payment payment = new Payment();
        payment.setOrder(order);
        when(paymentRepository.findByPgPaymentId("pi_123")).thenReturn(Optional.of(payment));
        when(disputeRepository.findByStripeDisputeId("dp_123")).thenReturn(Optional.empty());

        disputeService.openFromStripe(stripeDispute);

        Dispute saved = captureSaved();
        assertThat(saved.getStripeDisputeId()).isEqualTo("dp_123");
        assertThat(saved.getOrderId()).isEqualTo(55L);
        assertThat(saved.getAmount()).isEqualByComparingTo("84.99");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(DisputeStatus.NEEDS_RESPONSE);
        assertThat(saved.getEvidenceDueBy()).isNotNull();
        verify(outboxEventPublisher).publish(eq(OutboxEventTypes.DISPUTE_OPENED), any());
    }

    @Test
    void openFromStripe_onAKnownDispute_isAnUpdateNotASecondOpen() {
        Dispute existing = Dispute.openedFrom("dp_123", "pi_123", "ch_123", 55L,
                new java.math.BigDecimal("84.99"), "USD", "fraudulent", "needs_response", null);
        existing.setId(1L);
        when(disputeRepository.findByStripeDisputeId("dp_123")).thenReturn(Optional.of(existing));
        when(stripeDispute.getStatus()).thenReturn("under_review");

        disputeService.openFromStripe(stripeDispute);

        assertThat(existing.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        verify(outboxEventPublisher, never()).publish(eq(OutboxEventTypes.DISPUTE_OPENED), any());
    }

    @Test
    void syncFromStripe_onAnUnknownDispute_opensIt() {
        when(disputeRepository.findByStripeDisputeId("dp_123")).thenReturn(Optional.empty());
        when(paymentRepository.findByPgPaymentId("pi_123")).thenReturn(Optional.empty());

        disputeService.syncFromStripe(stripeDispute);

        verify(outboxEventPublisher).publish(eq(OutboxEventTypes.DISPUTE_OPENED), any());
    }

    @Test
    void syncFromStripe_toAWonState_closesAndPublishesOnce() {
        Dispute existing = Dispute.openedFrom("dp_123", "pi_123", "ch_123", 55L,
                new java.math.BigDecimal("84.99"), "USD", "fraudulent", "under_review", null);
        existing.setId(1L);
        when(disputeRepository.findByStripeDisputeId("dp_123")).thenReturn(Optional.of(existing));
        when(stripeDispute.getStatus()).thenReturn("won");

        disputeService.syncFromStripe(stripeDispute);

        assertThat(existing.getStatus()).isEqualTo(DisputeStatus.WON);
        assertThat(existing.getOutcomeNote()).contains("favour");
        verify(outboxEventPublisher).publish(eq(OutboxEventTypes.DISPUTE_CLOSED), any());
    }

    @Test
    void syncFromStripe_illegalBackwardTransitionIsIgnored() {
        Dispute existing = Dispute.openedFrom("dp_123", "pi_123", "ch_123", 55L,
                new java.math.BigDecimal("84.99"), "USD", "fraudulent", "won", null);
        existing.setId(1L);
        when(disputeRepository.findByStripeDisputeId("dp_123")).thenReturn(Optional.of(existing));
        when(stripeDispute.getStatus()).thenReturn("needs_response");

        disputeService.syncFromStripe(stripeDispute);

        assertThat(existing.getStatus()).as("stays WON").isEqualTo(DisputeStatus.WON);
        verify(outboxEventPublisher, never()).publish(eq(OutboxEventTypes.DISPUTE_CLOSED), any());
    }

    @Test
    void syncFromStripe_recordsEvidenceSubmission() {
        Dispute existing = Dispute.openedFrom("dp_123", "pi_123", "ch_123", 55L,
                new java.math.BigDecimal("84.99"), "USD", "fraudulent", "needs_response", null);
        existing.setId(1L);
        when(disputeRepository.findByStripeDisputeId("dp_123")).thenReturn(Optional.of(existing));
        when(evidenceDetails.getSubmissionCount()).thenReturn(1L);
        when(stripeDispute.getStatus()).thenReturn("under_review");

        disputeService.syncFromStripe(stripeDispute);

        assertThat(existing.getEvidenceSubmittedAt()).isNotNull();
        assertThat(existing.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
    }
}
