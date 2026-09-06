package com.ecommerce.project.service.pricing;

import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.repository.CouponRepository;
import com.ecommerce.project.service.CouponService;
import com.ecommerce.project.config.TaxProperties;
import com.ecommerce.project.service.pricing.rule.CouponDiscountRule;
import com.ecommerce.project.service.pricing.rule.ShippingRule;
import com.ecommerce.project.service.pricing.rule.VatRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
        PriceBreakdown breakdown = pipeline.price(new PricingContext(Money.of(110.0), us, List.of("SAVE20")));

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
        PriceBreakdown breakdown = wrongOrder.price(new PricingContext(Money.of(110.0), us, List.of("SAVE20")));

        assertThat(breakdown.shippingTotal()).isEqualTo(Money.of(0.0));
        assertThat(breakdown.total()).isEqualTo(Money.of(88.0));
    }

    @Test
    @DisplayName("runs coupon -> shipping -> VAT, so VAT lands on the discounted total plus shipping")
    void vatRunsLast() {
        when(couponRepository.findByCode("SAVE20"))
                .thenReturn(Optional.of(Coupon.builder().id(1L).code("SAVE20").discountPercent(20).build()));

        VatRule vatRule = new VatRule(new TaxRateResolver(
                new TaxProperties(true, BigDecimal.ZERO, true, Map.of("RO", new BigDecimal("19")))));

        // Spring injects the rules already @Order-sorted; the list here is in that order.
        PricingPipeline pipeline = new PricingPipeline(List.of(
                new CouponDiscountRule(couponRepository, couponService),
                new ShippingRule(new ShippingCalculator()),
                vatRule));

        Address ro = new Address(null, null, null, null, "Romania", null);
        // 110 - 20% = 88 -> below 100 -> +3 domestic shipping = 91 -> *19% = 17.29 -> 108.29
        PriceBreakdown breakdown = pipeline.price(new PricingContext(Money.of(110.0), ro, List.of("SAVE20")));

        assertThat(breakdown.discountTotal()).isEqualTo(Money.of(22.0));
        assertThat(breakdown.shippingTotal()).isEqualTo(Money.of(3.0));
        assertThat(breakdown.taxTotal()).isEqualTo(Money.of(17.29));
        assertThat(breakdown.total()).isEqualTo(Money.of(108.29));
    }

    @Test
    @DisplayName("no rules: total is just the subtotal")
    void noRules() {
        PricingPipeline pipeline = new PricingPipeline(List.of());
        PriceBreakdown breakdown = pipeline.price(new PricingContext(Money.of(42.0), null, List.of()));
        assertThat(breakdown.total()).isEqualTo(Money.of(42.0));
    }
}
