package com.ecommerce.project.service.outbox.payload;

/**
 * Outbox payload for {@code DISPUTE_OPENED} / {@code DISPUTE_CLOSED}: just the id
 * of the {@code Dispute} row. The handler reloads it, so the amount, reason and
 * deadline are read fresh rather than travelling through the queue and going
 * stale between the webhook and delivery.
 */
public record DisputeOutboxPayload(Long disputeId) {
}
