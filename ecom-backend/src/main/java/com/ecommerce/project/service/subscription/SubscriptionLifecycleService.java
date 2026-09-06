package com.ecommerce.project.service.subscription;

/**
 * The state transitions a {@code UserSubscription} goes through in response to
 * Stripe. Takes plain values, not Stripe objects, so the mapping from a webhook
 * payload lives in the thin {@code SubscriptionEventHandler}s and this stays
 * unit-testable without deep Stripe mocks.
 *
 * <p>Every method must run inside a transaction — {@code paymentFailed} and
 * {@code ended} enqueue an outbox notice, which is {@code MANDATORY}.
 */
public interface SubscriptionLifecycleService {

    /** {@code checkout.session.completed}: the customer paid; link the Stripe subscription and go ACTIVE. */
    void activateFromCheckout(String checkoutSessionId, String stripeSubscriptionId,
                              Long periodStartEpochSeconds, Long periodEndEpochSeconds);

    /** {@code invoice.payment_succeeded} for a renewal: extend the period, and clear PAST_DUE if it was set. */
    void renewed(String stripeSubscriptionId, Long periodEndEpochSeconds);

    /** {@code invoice.payment_failed}: mark PAST_DUE and enqueue a dunning notice. */
    void paymentFailed(String stripeSubscriptionId, Long nextAttemptEpochSeconds);

    /** {@code customer.subscription.updated}: mirror Stripe's status and period end. */
    void syncFromStripe(String stripeSubscriptionId, SubscriptionStatus status, Long periodEndEpochSeconds);

    /** {@code customer.subscription.deleted}: mark CANCELED and enqueue the "your subscription ended" notice. */
    void ended(String stripeSubscriptionId);
}
