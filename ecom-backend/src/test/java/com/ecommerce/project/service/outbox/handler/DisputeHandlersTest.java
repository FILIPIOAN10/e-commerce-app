package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.model.Dispute;
import com.ecommerce.project.repository.DisputeRepository;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.DisputeOutboxPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeHandlersTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private NotificationService notificationService;

    private final OutboxPayloadCodec codec = new OutboxPayloadCodec();
    private DisputeOpenedHandler openedHandler;
    private DisputeClosedHandler closedHandler;

    @BeforeEach
    void setUp() {
        openedHandler = new DisputeOpenedHandler(disputeRepository, notificationService, codec);
        closedHandler = new DisputeClosedHandler(disputeRepository, notificationService, codec);
    }

    private Dispute dispute(String stripeStatus, Long orderId) {
        Dispute d = Dispute.openedFrom("dp_9", "pi_9", "ch_9", orderId,
                new BigDecimal("84.99"), "USD", "fraudulent", stripeStatus,
                LocalDateTime.now().plusDays(7));
        d.setId(42L);
        return d;
    }

    @Test
    void openedHandler_alertsEveryAdminWithAmountReasonAndDeadline() {
        when(disputeRepository.findById(42L)).thenReturn(Optional.of(dispute("needs_response", 55L)));

        openedHandler.handle(codec.serialize(new DisputeOutboxPayload(42L)));

        verify(notificationService).notifyAdmins(
                eq("Chargeback opened"),
                contains("order #55"),
                eq("DISPUTE_OPENED"),
                eq(55L));
    }

    @Test
    void closedHandler_reportsALostOutcome() {
        Dispute lost = dispute("lost", 55L);
        lost.transitionTo(com.ecommerce.project.model.DisputeStatus.LOST, "lost");
        lost.setOutcomeNote("Resolved against us; funds withdrawn (lost).");
        when(disputeRepository.findById(42L)).thenReturn(Optional.of(lost));

        closedHandler.handle(codec.serialize(new DisputeOutboxPayload(42L)));

        verify(notificationService).notifyAdmins(
                eq("Chargeback lost"),
                contains("was lost"),
                eq("DISPUTE_CLOSED"),
                eq(55L));
    }

    @Test
    void closedHandler_reportsAWonOutcome() {
        Dispute won = dispute("won", null);
        won.transitionTo(com.ecommerce.project.model.DisputeStatus.WON, "won");
        when(disputeRepository.findById(42L)).thenReturn(Optional.of(won));

        closedHandler.handle(codec.serialize(new DisputeOutboxPayload(42L)));

        verify(notificationService).notifyAdmins(
                eq("Chargeback resolved"),
                contains("was won"),
                eq("DISPUTE_CLOSED"),
                isNull());
    }
}
