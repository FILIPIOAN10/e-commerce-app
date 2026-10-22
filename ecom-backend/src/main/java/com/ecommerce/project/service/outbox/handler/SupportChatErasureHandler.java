package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.SupportChatErasureOutboxPayload;
import com.ecommerce.project.service.support.SupportPilotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deletes a customer's conversations, approvals and agent checkpoints in
 * SupportPilot after a GDPR erasure.
 *
 * <p>On the outbox, not inside {@code GdprErasureService.erase}: that method is
 * one database transaction, and an HTTP call inside it would hold a pooled
 * connection on a remote service — and could not be undone if the transaction
 * then rolled back. Here the erasure commits first and this runs after, retried
 * with backoff until SupportPilot answers. Idempotent: SupportPilot's DELETE
 * answers 204 whether or not anything was left to delete.
 *
 * <p>Fails (and so retries, then dead-letters) while the assistant is disabled,
 * on purpose: personal data that may still sit in SupportPilot must not be
 * silently marked as erased.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportChatErasureHandler implements OutboxHandler {

    private final SupportPilotClient supportPilotClient;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.SUPPORT_CHAT_ERASURE;
    }

    @Override
    public void handle(String payload) {
        SupportChatErasureOutboxPayload data = payloadCodec.deserialize(payload, SupportChatErasureOutboxPayload.class);
        supportPilotClient.eraseCustomer(data.customerId());
        log.info("support conversations erased for {}", data.customerId());
    }
}
