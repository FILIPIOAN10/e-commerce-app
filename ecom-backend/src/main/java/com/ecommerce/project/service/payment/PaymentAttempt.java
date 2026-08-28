package com.ecommerce.project.service.payment;

import com.ecommerce.project.service.pricing.Money;

/**
 * Everything a {@link PaymentGateway} needs to check that an external payment
 * belongs to the order being placed.
 *
 * @param paymentMethod       the method chosen at checkout (e.g. {@code "STRIPE"},
 *                             {@code "online"}, {@code "COD"})
 * @param gatewayName         the payment-gateway name recorded on the request
 *                             (e.g. {@code "Stripe"}); may be {@code null}
 * @param gatewayPaymentId    the gateway's own reference for the payment
 *                             (e.g. a Stripe PaymentIntent id); may be blank when
 *                             no online payment was taken
 * @param expectedTotal       the order total the server computed, exact to the
 *                             cent; each gateway converts this to its own unit
 *                             as needed
 */
public record PaymentAttempt(String paymentMethod, String gatewayName,
                             String gatewayPaymentId, Money expectedTotal) {

    /** Whether an online payment reference is present and worth verifying. */
    public boolean hasReference() {
        return gatewayPaymentId != null && !gatewayPaymentId.isBlank();
    }
}
