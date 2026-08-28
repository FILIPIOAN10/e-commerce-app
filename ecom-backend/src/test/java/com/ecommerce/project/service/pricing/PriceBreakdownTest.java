package com.ecommerce.project.service.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PriceBreakdown")
class PriceBreakdownTest {

    @Test
    @DisplayName("running total starts at the subtotal")
    void startsAtSubtotal() {
        PriceBreakdown breakdown = new PriceBreakdown(120.0);
        assertThat(breakdown.subtotal()).isEqualTo(Money.of(120.0));
        assertThat(breakdown.runningTotal()).isEqualTo(Money.of(120.0));
        assertThat(breakdown.total()).isEqualTo(Money.of(120.0));
    }

    @Test
    @DisplayName("discounts lower the running total and are reported as a positive figure")
    void discountsAccumulate() {
        PriceBreakdown breakdown = new PriceBreakdown(100.0);
        breakdown.addDiscount("Coupon A", Money.of(10.0));   // 100 -> 90
        breakdown.addDiscount("Coupon B", Money.of(9.0));    // 90 -> 81

        assertThat(breakdown.runningTotal()).isEqualTo(Money.of(81.0));
        assertThat(breakdown.discountTotal()).isEqualTo(Money.of(19.0));
    }

    @Test
    @DisplayName("charges raise the running total and are summed by type")
    void chargesByType() {
        PriceBreakdown breakdown = new PriceBreakdown(50.0);
        breakdown.addCharge(PriceLineType.SHIPPING, "Shipping", Money.of(5.0));
        breakdown.addCharge(PriceLineType.TAX, "VAT", Money.of(11.0));

        assertThat(breakdown.shippingTotal()).isEqualTo(Money.of(5.0));
        assertThat(breakdown.taxTotal()).isEqualTo(Money.of(11.0));
        assertThat(breakdown.total()).isEqualTo(Money.of(66.0));
    }

    @Test
    @DisplayName("addCharge rejects DISCOUNT so discounts always go through addDiscount")
    void addChargeRejectsDiscountType() {
        PriceBreakdown breakdown = new PriceBreakdown(10.0);
        assertThatThrownBy(() -> breakdown.addCharge(PriceLineType.DISCOUNT, "x", Money.of(1.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("applied coupons are recorded for later consumption; the lists are copies")
    void recordsCoupons() {
        PriceBreakdown breakdown = new PriceBreakdown(10.0);
        breakdown.recordCoupon(7L, "SAVE10");

        assertThat(breakdown.appliedCouponIds()).containsExactly(7L);
        assertThat(breakdown.appliedCouponCodes()).containsExactly("SAVE10");
        assertThatThrownBy(() -> breakdown.appliedCouponIds().add(9L))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
