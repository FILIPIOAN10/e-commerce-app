package com.ecommerce.project.service.payment;

/**
 * A payment provider the checkout can verify an order's payment against
 * (Stripe today; PayPal, cash-on-delivery, etc. by adding an implementation).
 * <p>
 * This is the Strategy seam that keeps {@code OrderServiceImpl} from growing an
 * {@code if (isStripe) … else if (isPaypal) …} chain: a new provider is a new
 * {@code @Component} implementing this interface and nothing else changes.
 * Selection is delegated to each implementation via {@link #supports} so the
 * matching rules live next to the provider that owns them.
 */
public interface PaymentGateway {

    /** Stable identifier for this provider, used in logs and diagnostics. */
    String name();

    /** Whether this provider is the one that should verify the given attempt. */
    boolean supports(PaymentAttempt attempt);

    /**
     * Confirm that the external payment referenced by the attempt actually
     * succeeded, for this order, for the expected amount.
     */
    PaymentVerification verify(PaymentAttempt attempt);
}
