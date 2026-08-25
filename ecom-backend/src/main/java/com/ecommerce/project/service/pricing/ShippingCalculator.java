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

    private static final double FREE_SHIPPING_THRESHOLD = 100.0;
    private static final double BASE_COST = 5.0;
    private static final double DOMESTIC_COST = 3.0;

    public double calculate(Address address, double chargeableTotal) {
        if (chargeableTotal >= FREE_SHIPPING_THRESHOLD) {
            return 0.0;
        }
        boolean domestic = address != null
                && ("RO".equalsIgnoreCase(address.getCountry())
                 || "Romania".equalsIgnoreCase(address.getCountry()));
        return domestic ? DOMESTIC_COST : BASE_COST;
    }
}
