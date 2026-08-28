package com.ecommerce.project.service.pricing;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator threaded through the {@link PricingPipeline}. Each rule reads the
 * {@linkplain #runningTotal() running total} and appends {@link PriceLine}s; the
 * totals the order needs are then read back by type.
 * <p>
 * Every total is a {@link Money} in and out. The getters used to hand back
 * {@code double} because that is what the entities carried; now that the order
 * carries {@code BigDecimal}, a caller that still needs a {@code double} says so
 * with an explicit {@code toDouble()} — and each of those calls marks a place the
 * migration has not reached yet.
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

    public PriceBreakdown(Money subtotal) {
        this.subtotal = subtotal;
        this.runningTotal = subtotal;
    }

    /** Record a discount (a positive amount). */
    public void addDiscount(String label, Money amount) {
        discountTotal = discountTotal.add(amount);
        runningTotal = runningTotal.subtract(amount);
        lines.add(new PriceLine(label, PriceLineType.DISCOUNT, amount.negated().toBigDecimal()));
    }

    /** Record a charge — shipping, tax, … (a positive amount). */
    public void addCharge(PriceLineType type, String label, Money amount) {
        switch (type) {
            case SHIPPING -> shippingTotal = shippingTotal.add(amount);
            case TAX -> taxTotal = taxTotal.add(amount);
            case DISCOUNT -> throw new IllegalArgumentException("Use addDiscount for discounts");
        }
        runningTotal = runningTotal.add(amount);
        lines.add(new PriceLine(label, type, amount.toBigDecimal()));
    }

    /** Note a coupon that was applied, so the caller can consume its usage later. */
    public void recordCoupon(Long couponId, String code) {
        appliedCouponIds.add(couponId);
        appliedCouponCodes.add(code);
    }

    public Money subtotal() {
        return subtotal;
    }

    /** The running total so far, for rules that compute against it. */
    public Money runningTotal() {
        return runningTotal;
    }

    public Money total() {
        return runningTotal;
    }

    /** Total discount as a positive number. */
    public Money discountTotal() {
        return discountTotal;
    }

    public Money shippingTotal() {
        return shippingTotal;
    }

    public Money taxTotal() {
        return taxTotal;
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
