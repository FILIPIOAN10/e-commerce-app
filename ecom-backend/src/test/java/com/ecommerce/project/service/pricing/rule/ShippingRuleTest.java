package com.ecommerce.project.service.pricing.rule;

import com.ecommerce.project.model.Address;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.ShippingCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShippingRule")
class ShippingRuleTest {

    private final ShippingRule rule = new ShippingRule(new ShippingCalculator());

    private PriceBreakdown priceAfterDiscountOf(double subtotal, double discount, Address address) {
        PriceBreakdown breakdown = new PriceBreakdown(subtotal);
        if (discount > 0) {
            breakdown.addDiscount("test discount", Money.of(discount));
        }
        rule.apply(new PricingContext(subtotal, address, List.of()), breakdown);
        return breakdown;
    }

    @Test
    @DisplayName("charges shipping on the post-discount running total, not the subtotal")
    void chargesOnPostDiscountTotal() {
        Address us = new Address(null, null, null, null, "US", null);

        // Subtotal 110 would be free shipping, but a 20 discount drops it to 90 -> $5.
        PriceBreakdown breakdown = priceAfterDiscountOf(110.0, 20.0, us);

        assertThat(breakdown.shippingTotal()).isEqualTo(Money.of(5.0));
        assertThat(breakdown.total()).isEqualTo(Money.of(95.0));
    }

    @Test
    @DisplayName("free above the threshold")
    void freeAboveThreshold() {
        Address us = new Address(null, null, null, null, "US", null);
        PriceBreakdown breakdown = priceAfterDiscountOf(150.0, 0.0, us);
        assertThat(breakdown.shippingTotal()).isEqualTo(Money.of(0.0));
    }

    @Test
    @DisplayName("domestic (Romania) rate is lower")
    void domesticRate() {
        Address ro = new Address(null, null, null, null, "Romania", null);
        PriceBreakdown breakdown = priceAfterDiscountOf(40.0, 0.0, ro);
        assertThat(breakdown.shippingTotal()).isEqualTo(Money.of(3.0));
    }
}
