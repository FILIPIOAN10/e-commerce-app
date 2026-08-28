package com.ecommerce.project.service.pricing;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator threaded through the {@link PricingPipeline}. Each rule reads the
 * {@link #runningTotal()} and appends {@link PriceLine}s; the totals the order
 * needs are then read back by type.
 * <p>
 * Not thread-safe: one instance per pricing call.
 */
public final class PriceBreakdown {

    private final double subtotal;
    private double runningTotal;
    private final List<PriceLine> lines = new ArrayList<>();
    private final List<Long> appliedCouponIds = new ArrayList<>();
    private final List<String> appliedCouponCodes = new ArrayList<>();

    public PriceBreakdown(double subtotal) {
        this.subtotal = subtotal;
        this.runningTotal = subtotal;
    }

    /** Record a discount of {@code amount} (a positive number). */
    public void addDiscount(String label, double amount) {
        lines.add(new PriceLine(label, PriceLineType.DISCOUNT, -amount));
        runningTotal -= amount;
    }

    /** Record a charge (shipping, tax, …) of {@code amount} (a positive number). */
    public void addCharge(PriceLineType type, String label, double amount) {
        if (type == PriceLineType.DISCOUNT) {
            throw new IllegalArgumentException("Use addDiscount for discounts");
        }
        lines.add(new PriceLine(label, type, amount));
        runningTotal += amount;
    }

    /** Note a coupon that was applied, so the caller can consume its usage later. */
    public void recordCoupon(Long couponId, String code) {
        appliedCouponIds.add(couponId);
        appliedCouponCodes.add(code);
    }

    public double subtotal() {
        return subtotal;
    }

    public double runningTotal() {
        return runningTotal;
    }

    public double total() {
        return runningTotal;
    }

    /** Total discount as a positive number. */
    public double discountTotal() {
        return -sumOf(PriceLineType.DISCOUNT);
    }

    public double shippingTotal() {
        return sumOf(PriceLineType.SHIPPING);
    }

    public double taxTotal() {
        return sumOf(PriceLineType.TAX);
    }

    public List<PriceLine> lines() {
        return List.copyOf(lines);
    }

    public List<Long> appliedCouponIds() {
        return List.copyOf(appliedCouponIds);
    }

    public List<String> appliedCouponCodes() {
        return List.copyOf(appliedCouponCodes);
    }

    private double sumOf(PriceLineType type) {
        return lines.stream()
                .filter(line -> line.type() == type)
                .mapToDouble(PriceLine::amount)
                .sum();
    }
}
