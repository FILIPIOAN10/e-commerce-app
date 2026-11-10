package com.ecommerce.project.service.payment;

import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.service.pricing.Money;
import com.stripe.model.PaymentIntent;
import org.springframework.stereotype.Component;

/**
 * Verifies Stripe payments: the referenced PaymentIntent must be {@code succeeded}
 * and its amount must equal the order total, in cents of the settlement currency.
 * <p>
 * The amount comparison lives here rather than in the caller because it is
 * Stripe-specific — Stripe reports amounts as an integer number of the currency's
 * minor unit, and {@link Money#toCents()} is what maps the order total onto that.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private final StripeService stripeService;

    public StripePaymentGateway(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @Override
    public String name() {
        return "STRIPE";
    }

    @Override
    public boolean supports(PaymentAttempt attempt) {
        return "STRIPE".equalsIgnoreCase(attempt.paymentMethod())
                || "online".equalsIgnoreCase(attempt.paymentMethod())
                || "Stripe".equalsIgnoreCase(attempt.gatewayName());
    }

    @Override
    public PaymentVerification verify(PaymentAttempt attempt) {
        PaymentIntent intent = stripeService.retrievePaymentIntent(attempt.gatewayPaymentId());

        if (!"succeeded".equals(intent.getStatus())) {
            return PaymentVerification.failed("Payment has not succeeded");
        }

        long expectedCents = attempt.expectedTotal().toCents();
        if (intent.getAmount() == null || intent.getAmount() != expectedCents) {
            return PaymentVerification.failed("Payment amount does not match order total");
        }

        // The amount is a count of minor units, so it only means something
        // together with its currency: 8499 JPY is not $84.99. Checked here as
        // well as pinned at creation, because the intent id comes from the
        // client and could name one created some other way.
        if (!StripeService.SETTLEMENT_CURRENCY.equalsIgnoreCase(intent.getCurrency())) {
            return PaymentVerification.failed("Payment currency does not match the shop's settlement currency");
        }

        return PaymentVerification.ok();
    }
}
