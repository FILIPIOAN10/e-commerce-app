package com.ecommerce.project.service.outbox.payload;

/**
 * Outbox payload for {@code REFUND_REQUESTED}: just the id of the {@code Refund}
 * row written PENDING in the "mark refunded" transaction. The handler reloads it,
 * so nothing about the amount or the payment intent has to travel through the
 * queue and risk going stale.
 */
public record RefundOutboxPayload(Long refundId) {
}
