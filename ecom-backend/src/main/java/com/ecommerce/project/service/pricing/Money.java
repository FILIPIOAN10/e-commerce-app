package com.ecommerce.project.service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A currency amount, held as a {@link BigDecimal} at scale 2 with HALF_UP
 * rounding. Every operation returns a new {@code Money} re-rounded to the cent,
 * so a chain like {@code subtotal - 10% - 10% + shipping} stays exact instead of
 * accumulating the drift raw {@code double} math produces
 * (e.g. {@code 84.99 * 100 != 8499}).
 * <p>
 * Currency itself is not modelled yet — the store is single-currency. When that
 * changes this is where the currency code lives.
 */
public final class Money implements Comparable<Money> {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    /**
     * This amount counted {@code quantity} times — a line total. Exact: three of
     * 84.99 is 254.97, where {@code 84.99 * 3} in binary floating point is
     * 254.96999999999997.
     */
    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** {@code percent} percent of this amount, e.g. {@code percentage(10)} is a tenth. */
    public Money percentage(double percent) {
        BigDecimal fraction = BigDecimal.valueOf(percent)
                .divide(BigDecimal.valueOf(100), SCALE + 6, ROUNDING);
        return new Money(amount.multiply(fraction));
    }

    /** This amount with the opposite sign — a discount as it appears on a bill. */
    public Money negated() {
        return new Money(amount.negate());
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public Money max(Money other) {
        return amount.compareTo(other.amount) >= 0 ? this : other;
    }

    public double toDouble() {
        return amount.doubleValue();
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    /** Minor units (cents). Exact — the amount is already at scale 2. */
    public long toCents() {
        return amount.movePointRight(2).longValueExact();
    }

    /** Kept for the payment-gateway boundary; equivalent to {@code Money.of(amount).toCents()}. */
    public static long toCents(double amount) {
        return Money.of(amount).toCents();
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
