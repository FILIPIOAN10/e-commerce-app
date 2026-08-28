package com.ecommerce.project.service.outbox;

/**
 * Registry of outbox event type strings. Kept as constants so the publishing
 * listener and the consuming handler cannot drift apart on a typo.
 */
public final class OutboxEventTypes {

    private OutboxEventTypes() {
    }

    /** Customer order-confirmation email (with invoice PDF attached). */
    public static final String ORDER_CONFIRMATION_EMAIL = "ORDER_CONFIRMATION_EMAIL";

    /** Customer order status-change email. */
    public static final String ORDER_STATUS_EMAIL = "ORDER_STATUS_EMAIL";
}
