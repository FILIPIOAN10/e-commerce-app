package com.ecommerce.project.service.outbox.payload;

/**
 * Outbox payload for {@code SUBSCRIPTION_PAYMENT_FAILED} and
 * {@code SUBSCRIPTION_ENDED}: who to tell and about which plan. The webhook that
 * enqueues it already has both, so nothing has to be re-fetched by the handler.
 */
public record SubscriptionNoticeOutboxPayload(String email, String planName) {
}
