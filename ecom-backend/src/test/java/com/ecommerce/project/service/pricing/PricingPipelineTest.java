package com.ecommerce.project.service.pricing;

import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.repository.CouponRepository;
import com.ecommerce.project.service.CouponService;
import com.ecommerce.project.service.pricing.rule.CouponDiscountRule;
import com.ecommerce.project.service.pricing.rule.ShippingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingPipeline")
class PricingPipelineTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponService couponService;

    @Test
    @DisplayName("applies the coupon discount before shipping, so the discount can drop the order below the free-shipping threshold")
    void discountRunsBeforeShipping() {
        when(couponRepository.findByCode("SAVE20"))
                .thenReturn(Optional.of(Coupon.builder().id(1L).code("SAVE20").discountPercent(20).build()));

        PricingPipeline pipeline = new PricingPipeline(List.of(
                new CouponDiscountRule(couponRepository, couponService),
                new ShippingRule(new ShippingCalculator())));

        Address us = new Address(null, null, null, null, "US", null);
        // 110 subtotal -> 20% off -> 88 -> below $100 -> $5 shipping -> 93 total.
        PriceBreakdown breakdown = pipeline.price(new PricingContext(110.0, us, List.of("SAVE20")));

        assertThat(breakdown.subtotal()).isEqualTo(Money.of(110.0));
        assertThat(breakdown.discountTotal()).isEqualTo(Money.of(22.0));
        assertThat(breakdown.shippingTotal()).isEqualTo(Money.of(5.0));
        assertThat(breakdown.total()).isEqualTo(Money.of(93.0));
    }

    @Test
    @DisplayName("reversing the rule order would charge shipping on the pre-discount total")
    void orderMatters() {
        when(couponRepository.findByCode("SAVE20"))
                .thenReturn(Optional.of(Coupon.builder().id(1L).code("SAVE20").discountPercent(20).build()));

        // Shipping first (wrong order) sees 110 -> free shipping, then discount -> 88.
        PricingPipeline wrongOrder = new PricingPipeline(List.of(
                new ShippingRule(new ShippingCalculator()),
                new CouponDiscountRule(couponRepository, couponService)));

        Address us = new Address(null, null, null, null, "US", null);
        PriceBreakdown breakdown = wrongOrder.price(new PricingContext(110.0, us, List.of("SAVE20")));

        assertThat(breakdown.shippingTotal()).isEqualTo(Money.of(0.0));
        assertThat(breakdown.total()).isEqualTo(Money.of(88.0));
    }

    @Test
    @DisplayName("no rules: total is just the subtotal")
    void noRules() {
        PricingPipeline pipeline = new PricingPipeline(List.of());
        PriceBreakdown breakdown = pipeline.price(new PricingContext(42.0, null, List.of()));
        assertThat(breakdown.total()).isEqualTo(Money.of(42.0));
    }
}
