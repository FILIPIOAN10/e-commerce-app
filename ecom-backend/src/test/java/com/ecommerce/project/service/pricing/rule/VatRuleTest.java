package com.ecommerce.project.service.pricing.rule;

import com.ecommerce.project.config.TaxProperties;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PriceLineType;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.TaxRateResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VatRule")
class VatRuleTest {

    private static final Address RO = new Address(null, null, null, null, "Romania", null);

    private VatRule rule(boolean enabled, boolean taxableShipping) {
        TaxProperties props = new TaxProperties(
                enabled, BigDecimal.ZERO, taxableShipping, Map.of("RO", new BigDecimal("19")));
        return new VatRule(new TaxRateResolver(props));
    }

    /** A breakdown after a discount and a shipping charge, i.e. the state VatRule sees at @Order(30). */
    private PriceBreakdown afterDiscountAndShipping(double subtotal, double discount, double shipping) {
        PriceBreakdown breakdown = new PriceBreakdown(Money.of(subtotal));
        if (discount > 0) {
            breakdown.addDiscount("test discount", Money.of(discount));
        }
        if (shipping > 0) {
            breakdown.addCharge(PriceLineType.SHIPPING, "Shipping", Money.of(shipping));
        }
        return breakdown;
    }

    @Test
    @DisplayName("charges VAT on the post-discount total plus shipping")
    void taxesDiscountedTotalWithShipping() {
        // 100 - 20 discount = 80, + 5 shipping = 85, * 19% = 16.15
        PriceBreakdown breakdown = afterDiscountAndShipping(100.0, 20.0, 5.0);
        rule(true, true).apply(new PricingContext(Money.of(100.0), RO, List.of()), breakdown);

        assertThat(breakdown.taxTotal()).isEqualTo(Money.of(16.15));
        assertThat(breakdown.total()).isEqualTo(Money.of(101.15));
        assertThat(breakdown.lines())
                .anySatisfy(line -> {
                    assertThat(line.type()).isEqualTo(PriceLineType.TAX);
                    assertThat(line.label()).isEqualTo("VAT (19%)");
                });
    }

    @Test
    @DisplayName("app.tax.taxable-shipping=false leaves shipping out of the base")
    void shippingExcludedFromBase() {
        // base is 80 (not 85), * 19% = 15.20
        PriceBreakdown breakdown = afterDiscountAndShipping(100.0, 20.0, 5.0);
        rule(true, false).apply(new PricingContext(Money.of(100.0), RO, List.of()), breakdown);

        assertThat(breakdown.taxTotal()).isEqualTo(Money.of(15.20));
    }

    @Test
    @DisplayName("disabled: adds no line at all, not a zero one")
    void disabledAddsNothing() {
        PriceBreakdown breakdown = afterDiscountAndShipping(100.0, 0.0, 5.0);
        rule(false, true).apply(new PricingContext(Money.of(100.0), RO, List.of()), breakdown);

        assertThat(breakdown.taxTotal()).isEqualTo(Money.ZERO);
        assertThat(breakdown.lines()).noneMatch(line -> line.type() == PriceLineType.TAX);
    }

    @Test
    @DisplayName("a destination with no configured rate and a zero default adds no line")
    void unknownDestinationNoDefault() {
        Address us = new Address(null, null, null, null, "US", null);
        PriceBreakdown breakdown = afterDiscountAndShipping(100.0, 0.0, 5.0);
        rule(true, true).apply(new PricingContext(Money.of(100.0), us, List.of()), breakdown);

        assertThat(breakdown.lines()).noneMatch(line -> line.type() == PriceLineType.TAX);
    }
}
