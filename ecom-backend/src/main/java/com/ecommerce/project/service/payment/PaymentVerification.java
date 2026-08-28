package com.ecommerce.project.service.payment;

/**
 * Outcome of {@link PaymentGateway#verify(PaymentAttempt)}.
 * <p>
 * A result rather than a bare {@code boolean} so the caller can surface
 * <em>why</em> a payment was rejected (wrong amount vs. not yet succeeded)
 * instead of a single opaque failure.
 */
public record PaymentVerification(boolean verified, String reason) {

    private static final PaymentVerification OK = new PaymentVerification(true, null);

    public static PaymentVerification ok() {
        return OK;
    }

    public static PaymentVerification failed(String reason) {
        return new PaymentVerification(false, reason);
    }
}
