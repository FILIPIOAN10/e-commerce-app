package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.model.Dispute;
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

/**
 * Alerts every admin that a chargeback was opened, with the amount, reason and
 * the deadline to file evidence. Re-delivery just re-sends the alert — harmless,
 * and better than silently dropping it on the one delivery that mattered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeOpenedHandler implements OutboxHandler {

    private final DisputeRepository disputeRepository;
    private final NotificationService notificationService;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.DISPUTE_OPENED;
    }

    @Override
    @Transactional
    public void handle(String payload) {
        DisputeOutboxPayload data = payloadCodec.deserialize(payload, DisputeOutboxPayload.class);
        Dispute dispute = disputeRepository.findById(data.disputeId())
                .orElseThrow(() -> new IllegalStateException("Dispute " + data.disputeId() + " no longer exists"));

        String orderRef = dispute.getOrderId() != null ? "order #" + dispute.getOrderId() : "an unlinked charge";
        String deadline = dispute.getEvidenceDueBy() != null
                ? " Evidence due by " + dispute.getEvidenceDueBy() + "."
                : "";
        String message = String.format("Chargeback on %s — %s %s, reason: %s.%s",
                orderRef, dispute.getAmount(), dispute.getCurrency(),
                dispute.getReason() != null ? dispute.getReason() : "unspecified", deadline);

        notificationService.notifyAdmins("Chargeback opened", message, "DISPUTE_OPENED", dispute.getOrderId());
        log.info("Admins alerted to dispute {}", dispute.getStripeDisputeId());
    }
}
