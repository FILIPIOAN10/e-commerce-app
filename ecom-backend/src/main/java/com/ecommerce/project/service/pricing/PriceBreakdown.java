package com.ecommerce.project.service.pricing;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator threaded through the {@link PricingPipeline}. Each rule reads the
 * {@linkplain #currentTotal() running total} and appends {@link PriceLine}s; the
 * totals the order needs are then read back by type.
 * <p>
 * Arithmetic is done in {@link Money} (exact to the cent); the getters return
 * {@code double} because that is still what the entities and DTOs carry.
 * <p>
 * Not thread-safe: one instance per pricing call.
 */
public final class PriceBreakdown {

    private final Money subtotal;
    private Money runningTotal;
    private Money discountTotal = Money.ZERO;
    private Money shippingTotal = Money.ZERO;
    private Money taxTotal = Money.ZERO;

    private final List<PriceLine> lines = new ArrayList<>();
    private final List<Long> appliedCouponIds = new ArrayList<>();
    private final List<String> appliedCouponCodes = new ArrayList<>();

    public PriceBreakdown(double subtotal) {
        this.subtotal = Money.of(subtotal);
        this.runningTotal = this.subtotal;
    }

    /** Record a discount (a positive amount). */
    public void addDiscount(String label, Money amount) {
        discountTotal = discountTotal.add(amount);
        runningTotal = runningTotal.subtract(amount);
        lines.add(new PriceLine(label, PriceLineType.DISCOUNT, -amount.toDouble()));
    }

    /** Record a charge — shipping, tax, … (a positive amount). */
    public void addCharge(PriceLineType type, String label, Money amount) {
        switch (type) {
            case SHIPPING -> shippingTotal = shippingTotal.add(amount);
            case TAX -> taxTotal = taxTotal.add(amount);
            case DISCOUNT -> throw new IllegalArgumentException("Use addDiscount for discounts");
        }
        runningTotal = runningTotal.add(amount);
        lines.add(new PriceLine(label, type, amount.toDouble()));
    }

    /** Note a coupon that was applied, so the caller can consume its usage later. */
    public void recordCoupon(Long couponId, String code) {
        appliedCouponIds.add(couponId);
        appliedCouponCodes.add(code);
    }

    /** The running total so far, for rules that compute against it. */
    public Money currentTotal() {
        return runningTotal;
    }

    public double subtotal() {
        return subtotal.toDouble();
    }

    public double runningTotal() {
        return runningTotal.toDouble();
    }

    public double total() {
        return runningTotal.toDouble();
    }

    /** Total discount as a positive number. */
    public double discountTotal() {
        return discountTotal.toDouble();
    }

    public double shippingTotal() {
        return shippingTotal.toDouble();
    }

    public double taxTotal() {
        return taxTotal.toDouble();
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
}
