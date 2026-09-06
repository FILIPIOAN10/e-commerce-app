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

    /** A subscription renewal payment failed — tell the customer to fix their card. */
    public static final String SUBSCRIPTION_PAYMENT_FAILED = "SUBSCRIPTION_PAYMENT_FAILED";

    /** A subscription has ended (cancelled, or dunning ran out) — tell the customer. */
    public static final String SUBSCRIPTION_ENDED = "SUBSCRIPTION_ENDED";

    /** A chargeback was opened on an order — alert the admins, with the evidence deadline. */
    public static final String DISPUTE_OPENED = "DISPUTE_OPENED";

    /** A chargeback reached a terminal state (won / lost / closed) — alert the admins of the outcome. */
    public static final String DISPUTE_CLOSED = "DISPUTE_CLOSED";
}
