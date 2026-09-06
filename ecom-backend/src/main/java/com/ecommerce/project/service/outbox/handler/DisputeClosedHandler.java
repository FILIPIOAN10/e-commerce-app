package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.model.Dispute;
import com.ecommerce.project.model.DisputeStatus;
import com.ecommerce.project.repository.DisputeRepository;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.DisputeOutboxPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tells the admins how a chargeback ended (won / lost / closed). */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeClosedHandler implements OutboxHandler {

    private final DisputeRepository disputeRepository;
    private final NotificationService notificationService;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.DISPUTE_CLOSED;
    }

    @Override
    @Transactional
    public void handle(String payload) {
        DisputeOutboxPayload data = payloadCodec.deserialize(payload, DisputeOutboxPayload.class);
        Dispute dispute = disputeRepository.findById(data.disputeId())
                .orElseThrow(() -> new IllegalStateException("Dispute " + data.disputeId() + " no longer exists"));

        String orderRef = dispute.getOrderId() != null ? "order #" + dispute.getOrderId() : "an unlinked charge";
        String verb = switch (dispute.getStatus()) {
            case WON -> "won";
            case LOST -> "lost";
            default -> "closed";
        };
        String title = dispute.getStatus() == DisputeStatus.LOST ? "Chargeback lost" : "Chargeback resolved";
        String message = String.format("Chargeback on %s (%s %s) was %s. %s",
                orderRef, dispute.getAmount(), dispute.getCurrency(), verb,
                dispute.getOutcomeNote() != null ? dispute.getOutcomeNote() : "");

        notificationService.notifyAdmins(title, message.trim(), "DISPUTE_CLOSED", dispute.getOrderId());
        log.info("Admins notified dispute {} {}", dispute.getStripeDisputeId(), verb);
    }
}
