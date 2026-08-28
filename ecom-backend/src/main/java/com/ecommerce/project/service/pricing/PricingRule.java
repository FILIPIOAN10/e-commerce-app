package com.ecommerce.project.service.pricing;

/**
 * One step in pricing an order — a coupon discount, shipping, tax, …
 * <p>
 * Rules run in {@code @Order} sequence and each mutates the shared
 * {@link PriceBreakdown}. Ordering is meaningful: shipping's free-shipping
 * threshold is checked against the post-discount running total, so
 * {@code CouponDiscountRule} must run before {@code ShippingRule}. Making that a
 * declared {@code @Order} rather than the sequence of statements in one method
 * is the whole point of the pipeline.
 */
public interface PricingRule {

    void apply(PricingContext context, PriceBreakdown breakdown);
}
