package com.ecommerce.project.service.payment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Picks the {@link PaymentGateway} that should verify a given {@link PaymentAttempt}.
 * <p>
 * Spring injects every {@code PaymentGateway} bean into the constructor, so adding
 * a provider needs no change here. When no provider claims the attempt (e.g.
 * cash-on-delivery with no gateway reference) the caller simply skips gateway
 * verification — the same behaviour the old {@code isStripePayment(...)} guard had.
 */
@Component
public class PaymentGatewayRegistry {

    private final List<PaymentGateway> gateways;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.gateways = List.copyOf(gateways);
    }

    public Optional<PaymentGateway> select(PaymentAttempt attempt) {
        return gateways.stream()
                .filter(gateway -> gateway.supports(attempt))
                .findFirst();
    }
}
