package com.ecommerce.project.model;

/**
 * Lifecycle of a {@link Refund}.
 *
 * <ul>
 *   <li>{@code PENDING} — recorded in the "mark refunded" transaction; the Stripe
 *       call has not completed yet (the outbox handler owns it).</li>
 *   <li>{@code SUCCEEDED} — Stripe accepted the refund; {@code stripe_refund_id} is set.</li>
 *   <li>{@code FAILED} — Stripe rejected it for a reason retrying will not fix
 *       (already refunded elsewhere, charge not refundable); an admin is notified.</li>
 * </ul>
 *
 * <p>A transient failure (network, rate limit, 5xx) never lands here — it is
 * thrown so the outbox backs off and retries.
 */
public enum RefundStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
