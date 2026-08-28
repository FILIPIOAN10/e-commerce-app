package com.ecommerce.project.service.pricing.rule;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.repository.CouponRepository;
import com.ecommerce.project.service.CouponService;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PricingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponDiscountRule")
class CouponDiscountRuleTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponService couponService;

    private CouponDiscountRule rule;

    @BeforeEach
    void setUp() {
        rule = new CouponDiscountRule(couponRepository, couponService);
    }

    private Coupon coupon(long id, String code, int percent) {
        Coupon c = Coupon.builder().id(id).code(code).discountPercent(percent).build();
        return c;
    }

    private PriceBreakdown apply(List<String> codes, double subtotal) {
        PriceBreakdown breakdown = new PriceBreakdown(subtotal);
        rule.apply(new PricingContext(subtotal, null, codes), breakdown);
        return breakdown;
    }

    @Test
    @DisplayName("no codes: leaves the total untouched and records nothing")
    void noCodes() {
        PriceBreakdown breakdown = apply(null, 100.0);
        assertThat(breakdown.runningTotal()).isEqualTo(100.0);
        assertThat(breakdown.appliedCouponIds()).isEmpty();
    }

    @Test
    @DisplayName("stacks percentages on the running total, not the original subtotal")
    void stacksOnRunningTotal() {
        when(couponRepository.findByCode("A")).thenReturn(Optional.of(coupon(1L, "A", 10)));
        when(couponRepository.findByCode("B")).thenReturn(Optional.of(coupon(2L, "B", 10)));

        PriceBreakdown breakdown = apply(List.of("A", "B"), 100.0);

        // 100 - 10% = 90, then 90 - 10% = 81  (two 10% coupons are not 20%)
        assertThat(breakdown.runningTotal()).isEqualTo(81.0, within(1e-9));
        assertThat(breakdown.discountTotal()).isEqualTo(19.0, within(1e-9));
        assertThat(breakdown.appliedCouponIds()).containsExactly(1L, 2L);
        assertThat(breakdown.appliedCouponCodes()).containsExactly("A", "B");
    }

    @Test
    @DisplayName("uppercases the code before lookup and skips blank entries")
    void normalisesCode() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon(1L, "SAVE10", 20)));

        PriceBreakdown breakdown = apply(List.of("  ", "save10"), 50.0);

        verify(couponRepository).findByCode("SAVE10");
        assertThat(breakdown.discountTotal()).isEqualTo(10.0, within(1e-9));
    }

    @Test
    @DisplayName("unknown code fails the pricing call")
    void unknownCode() {
        when(couponRepository.findByCode(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> apply(List.of("NOPE"), 100.0))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Invalid coupon code: NOPE");
    }

    @Test
    @DisplayName("delegates state checks to CouponService and never consumes usage")
    void validatesAndDoesNotConsume() {
        Coupon expired = coupon(1L, "OLD", 10);
        when(couponRepository.findByCode("OLD")).thenReturn(Optional.of(expired));
        doThrow(new APIException("Coupon has expired")).when(couponService).validateCouponState(expired, "OLD");

        assertThatThrownBy(() -> apply(List.of("OLD"), 100.0))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("expired");

        verify(couponRepository, never()).tryConsume(anyLong());
    }
}
