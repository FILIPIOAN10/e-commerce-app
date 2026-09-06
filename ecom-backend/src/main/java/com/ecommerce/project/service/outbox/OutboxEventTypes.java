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

    /** Abandoned-cart recovery reminder email. */
    public static final String CART_ABANDONMENT_REMINDER = "CART_ABANDONMENT_REMINDER";

    /** Build a GDPR Art. 15 data export and email its download link. */
    public static final String GDPR_EXPORT_REQUESTED = "GDPR_EXPORT_REQUESTED";

    /** Issue the Stripe refund for a return an admin has marked refunded. */
    public static final String REFUND_REQUESTED = "REFUND_REQUESTED";
}
