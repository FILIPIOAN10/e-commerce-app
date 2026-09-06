package com.ecommerce.project.service.subscription;

/**
 * The lifecycle of a {@code UserSubscription}, kept as a typed enum the way
 * {@code OrderStatus} and {@code PaymentStatus} are — a webhook that reports a
 * status Stripe added tomorrow maps to {@link #INCOMPLETE} rather than being
 * written verbatim into the column. Persisted as {@link #name()} in
 * {@code user_subscriptions.status}.
 */
public enum SubscriptionStatus {

    /** Checkout session created, payment not yet confirmed. */
    PENDING,
    /** Paid and current (Stripe {@code active} or {@code trialing}). */
    ACTIVE,
    /** A renewal payment failed; Stripe is retrying (dunning). */
    PAST_DUE,
    /** Ended — cancelled by the customer or by Stripe after dunning ran out. */
    CANCELED,
    /** Stripe gave up collecting; the subscription is unpaid and inactive. */
    UNPAID,
    /** First payment not completed. */
    INCOMPLETE,
    /** First payment window expired without a successful charge. */
    INCOMPLETE_EXPIRED,
    /** Explicitly paused. */
    PAUSED;

    public static SubscriptionStatus fromStripe(String stripeStatus) {
        return switch (stripeStatus == null ? "" : stripeStatus) {
            case "active", "trialing" -> ACTIVE;
            case "past_due" -> PAST_DUE;
            case "canceled" -> CANCELED;
            case "unpaid" -> UNPAID;
            case "incomplete" -> INCOMPLETE;
            case "incomplete_expired" -> INCOMPLETE_EXPIRED;
            case "paused" -> PAUSED;
            default -> INCOMPLETE;
        };
    }

    /** No further billing will happen — the customer has lost access. */
    public boolean isEnded() {
        return this == CANCELED || this == INCOMPLETE_EXPIRED || this == UNPAID;
    }
}
