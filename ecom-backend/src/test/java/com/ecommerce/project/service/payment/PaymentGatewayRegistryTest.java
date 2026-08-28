package com.ecommerce.project.service.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentGatewayRegistry")
class PaymentGatewayRegistryTest {

    private static PaymentAttempt attempt(String method, String gatewayName) {
        return new PaymentAttempt(method, gatewayName, "pi_1", 10.0);
    }

    /** Minimal stub gateway that claims one method name. */
    private static PaymentGateway gatewayFor(String method) {
        return new PaymentGateway() {
            @Override
            public String name() {
                return method;
            }

            @Override
            public boolean supports(PaymentAttempt a) {
                return method.equalsIgnoreCase(a.paymentMethod());
            }

            @Override
            public PaymentVerification verify(PaymentAttempt a) {
                return PaymentVerification.ok();
            }
        };
    }

    @Test
    @DisplayName("selects the gateway that claims the attempt")
    void selectsSupportingGateway() {
        PaymentGatewayRegistry registry =
                new PaymentGatewayRegistry(List.of(gatewayFor("PAYPAL"), gatewayFor("STRIPE")));

        assertThat(registry.select(attempt("STRIPE", "Stripe")))
                .get()
                .extracting(PaymentGateway::name)
                .isEqualTo("STRIPE");
    }

    @Test
    @DisplayName("returns empty when no gateway claims the attempt")
    void returnsEmptyForUnknownMethod() {
        PaymentGatewayRegistry registry =
                new PaymentGatewayRegistry(List.of(gatewayFor("STRIPE")));

        assertThat(registry.select(attempt("COD", null))).isEmpty();
    }

    @Test
    @DisplayName("returns empty when there are no gateways at all")
    void returnsEmptyWithNoGateways() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of());

        assertThat(registry.select(attempt("STRIPE", "Stripe"))).isEmpty();
    }

    @Test
    @DisplayName("returns the first gateway when several would claim the attempt")
    void returnsFirstMatch() {
        PaymentGateway first = gatewayFor("STRIPE");
        PaymentGateway second = gatewayFor("STRIPE");
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(first, second));

        assertThat(registry.select(attempt("STRIPE", "Stripe"))).containsSame(first);
    }
}
