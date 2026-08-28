package com.ecommerce.project.service.pricing.rule;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.repository.CouponRepository;
import com.ecommerce.project.service.CouponService;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.PricingRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies stacked percentage coupons. Each coupon's percentage is taken off the
 * running total <em>after</em> earlier coupons, so order of entry matters and
 * two 10% coupons are not 20%.
 * <p>
 * Pure calculation — coupon usage counters are not touched here. The applied
 * coupon ids are recorded on the breakdown so checkout can consume them
 * atomically after the order commits; {@code previewOrder} simply ignores them.
 */
@Component
@Order(10)
public class CouponDiscountRule implements PricingRule {

    private final CouponRepository couponRepository;
    private final CouponService couponService;

    public CouponDiscountRule(CouponRepository couponRepository, CouponService couponService) {
        this.couponRepository = couponRepository;
        this.couponService = couponService;
    }

    @Override
    public void apply(PricingContext context, PriceBreakdown breakdown) {
        List<String> codes = context.couponCodes();
        if (codes == null || codes.isEmpty()) {
            return;
        }

        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }

            Coupon coupon = couponRepository.findByCode(code.toUpperCase())
                    .orElseThrow(() -> new APIException("Invalid coupon code: " + code));

            couponService.validateCouponState(coupon, code);

            Money discount = breakdown.runningTotal().percentage(coupon.getDiscountPercent());
            breakdown.addDiscount("Coupon " + coupon.getCode(), discount);
            breakdown.recordCoupon(coupon.getId(), coupon.getCode());
        }
    }
}
