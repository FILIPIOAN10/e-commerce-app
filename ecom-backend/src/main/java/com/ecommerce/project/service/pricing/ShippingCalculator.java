package com.ecommerce.project.service.pricing;

import com.ecommerce.project.model.Address;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for shipping-cost calculation.
 * Shipping is always computed from the post-discount total that the customer
 * actually pays, because the free-shipping threshold is a customer-facing
 * promotion on the final chargeable amount.
 */
@Component
public class ShippingCalculator {

    private static final Money FREE_SHIPPING_THRESHOLD = Money.of(100.0);
    private static final Money BASE_COST = Money.of(5.0);
    private static final Money DOMESTIC_COST = Money.of(3.0);

    public Money calculate(Address address, Money chargeableTotal) {
        if (chargeableTotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return Money.ZERO;
        }
        boolean domestic = address != null
                && ("RO".equalsIgnoreCase(address.getCountry())
                 || "Romania".equalsIgnoreCase(address.getCountry()));
        return domestic ? DOMESTIC_COST : BASE_COST;
    }
}
