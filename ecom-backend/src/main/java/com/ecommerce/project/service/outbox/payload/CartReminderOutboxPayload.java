package com.ecommerce.project.service.outbox.payload;

/**
 * Outbox payload for an abandoned-cart reminder email. Everything the template
 * needs is captured here, so the handler never has to re-load the cart (which
 * could have changed or emptied between enqueue and send).
 *
 * @param recipientEmail who to email
 * @param recipientName  display name for the greeting
 * @param cartId         the abandoned cart
 * @param stage          which reminder in the sequence (FIRST / SECOND / FINAL)
 * @param itemCount      number of items in the cart at enqueue time
 * @param cartTotal      cart total at enqueue time
 * @param recoveryUrl    signed, expiring link back to the cart
 */
public record CartReminderOutboxPayload(
        String recipientEmail,
        String recipientName,
        Long cartId,
        String stage,
        int itemCount,
        double cartTotal,
        String recoveryUrl) {
}
