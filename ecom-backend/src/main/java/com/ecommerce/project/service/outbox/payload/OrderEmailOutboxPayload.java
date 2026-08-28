package com.ecommerce.project.service.outbox.payload;

import com.ecommerce.project.payload.OrderDTO;

/**
 * Outbox payload for the order emails: the recipient plus the finished
 * {@link OrderDTO} the templates render from. The DTO is already detached plain
 * data (see {@code OrderPlacedEvent}), so it serialises to JSON cleanly.
 */
public record OrderEmailOutboxPayload(String recipientEmail, OrderDTO order) {
}
