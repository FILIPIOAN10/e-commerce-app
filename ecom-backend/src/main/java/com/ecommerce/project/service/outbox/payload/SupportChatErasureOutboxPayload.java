package com.ecommerce.project.service.outbox.payload;

/**
 * Outbox payload for erasing a customer's SupportPilot conversations.
 *
 * @param customerId the SupportPilot-side id, {@code u-<userId>}
 */
public record SupportChatErasureOutboxPayload(String customerId) {
}
