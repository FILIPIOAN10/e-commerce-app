package com.ecommerce.project.service.payment;

import com.ecommerce.project.service.pricing.Money;

import com.ecommerce.project.service.StripeService;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StripePaymentGateway")
class StripePaymentGatewayTest {

    @Mock
    private StripeService stripeService;

    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new StripePaymentGateway(stripeService);
    }

    private PaymentAttempt attempt(String method, String gatewayName, double total) {
        return new PaymentAttempt(method, gatewayName, "pi_123", Money.of(total));
    }

    @Test
    @DisplayName("claims STRIPE / online payment methods and a 'Stripe' gateway name")
    void supportsStripeIdentifiers() {
        assertThat(gateway.supports(attempt("STRIPE", null, 10))).isTrue();
        assertThat(gateway.supports(attempt("stripe", null, 10))).isTrue();
        assertThat(gateway.supports(attempt("online", null, 10))).isTrue();
        assertThat(gateway.supports(attempt("card", "Stripe", 10))).isTrue();
    }

    @Test
    @DisplayName("does not claim other payment methods")
    void doesNotSupportOthers() {
        assertThat(gateway.supports(attempt("COD", null, 10))).isFalse();
        assertThat(gateway.supports(attempt("PAYPAL", "PayPal", 10))).isFalse();
    }

    @Test
    @DisplayName("verifies a succeeded intent whose amount matches the order total")
    void verifiesMatchingSucceededIntent() {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(intent.getAmount()).thenReturn(8499L);
        when(stripeService.retrievePaymentIntent(anyString())).thenReturn(intent);

        PaymentVerification result = gateway.verify(attempt("STRIPE", "Stripe", 84.99));

        assertThat(result.verified()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("rejects an intent that has not succeeded")
    void rejectsUnsucceededIntent() {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("requires_payment_method");
        when(stripeService.retrievePaymentIntent(anyString())).thenReturn(intent);

        PaymentVerification result = gateway.verify(attempt("STRIPE", "Stripe", 84.99));

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).isEqualTo("Payment has not succeeded");
    }

    @Test
    @DisplayName("rejects a succeeded intent whose amount does not match")
    void rejectsAmountMismatch() {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(intent.getAmount()).thenReturn(9999L);
        when(stripeService.retrievePaymentIntent(anyString())).thenReturn(intent);

        PaymentVerification result = gateway.verify(attempt("STRIPE", "Stripe", 84.99));

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).isEqualTo("Payment amount does not match order total");
    }

    @Test
    @DisplayName("rejects a succeeded intent with a null amount")
    void rejectsNullAmount() {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(intent.getAmount()).thenReturn(null);
        when(stripeService.retrievePaymentIntent(anyString())).thenReturn(intent);

        PaymentVerification result = gateway.verify(attempt("STRIPE", "Stripe", 84.99));

        assertThat(result.verified()).isFalse();
        assertThat(result.reason()).isEqualTo("Payment amount does not match order total");
    }
}
