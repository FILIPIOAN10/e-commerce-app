package com.ecommerce.project.service.payment;

/**
 * What Stripe reported for a refund we asked it to create. Deliberately not the
 * {@code com.stripe.model.Refund} type — the gateway detail stops at the
 * {@code StripeService} boundary, the rest of the app only needs the id to
 * de-duplicate on and the status to decide retry-or-not.
 *
 * @param stripeRefundId Stripe's {@code re_...} id
 * @param status         Stripe's status string: {@code succeeded}, {@code pending},
 *                       {@code failed}, {@code canceled}, {@code requires_action}
 */
public record RefundResult(String stripeRefundId, String status) {
}
