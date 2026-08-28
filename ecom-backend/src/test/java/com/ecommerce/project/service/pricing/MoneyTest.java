package com.ecommerce.project.service.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Money")
class MoneyTest {

    @Test
    @DisplayName("holds a scale-2 amount and rounds HALF_UP on construction")
    void roundsToCents() {
        assertThat(Money.of(10.005).toBigDecimal()).isEqualByComparingTo("10.01");
        assertThat(Money.of(10.004).toBigDecimal()).isEqualByComparingTo("10.00");
        assertThat(Money.of(new BigDecimal("2.999")).toDouble()).isEqualTo(3.00);
    }

    @Test
    @DisplayName("add and subtract stay exact where raw double math would drift")
    void exactArithmetic() {
        Money total = Money.of(0.10).add(Money.of(0.20));   // 0.1 + 0.2 != 0.3 in double
        assertThat(total.toBigDecimal()).isEqualByComparingTo("0.30");

        assertThat(Money.of(84.99).subtract(Money.of(0.00)).toCents()).isEqualTo(8499L);
    }

    @Test
    @DisplayName("stacked percentages compound on the reduced amount")
    void stackedPercentages() {
        // 100 - 10% = 90, then 90 - 10% = 81
        Money afterFirst = Money.of(100.0).subtract(Money.of(100.0).percentage(10));
        Money afterSecond = afterFirst.subtract(afterFirst.percentage(10));

        assertThat(afterFirst.toBigDecimal()).isEqualByComparingTo("90.00");
        assertThat(afterSecond.toBigDecimal()).isEqualByComparingTo("81.00");
    }

    @Test
    @DisplayName("percentage of an odd amount rounds to the cent")
    void percentageRounds() {
        // 20% of 105.00 = 21.00 exactly; 15% of 33.33 = 4.9995 -> 5.00
        assertThat(Money.of(105.0).percentage(20).toBigDecimal()).isEqualByComparingTo("21.00");
        assertThat(Money.of(33.33).percentage(15).toBigDecimal()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("toCents is exact for a chained checkout total")
    void toCentsForChainedTotal() {
        // 105 subtotal, 20% coupon -> 84.00, + 5.00 shipping -> 89.00 -> 8900c
        Money subtotal = Money.of(105.0);
        Money afterCoupon = subtotal.subtract(subtotal.percentage(20));
        Money total = afterCoupon.add(Money.of(5.0));

        assertThat(total.toCents()).isEqualTo(8900L);
    }

    @Test
    @DisplayName("equality is by value; sign helpers")
    void valueSemantics() {
        assertThat(Money.of(5.0)).isEqualTo(Money.of(5.00));
        assertThat(Money.of(5.0)).hasSameHashCodeAs(Money.of(5.000));
        assertThat(Money.of(-1.0).isNegative()).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of(3.0).max(Money.of(7.0))).isEqualTo(Money.of(7.0));
    }

    @Test
    @DisplayName("static toCents(double) matches the instance method")
    void staticToCents() {
        assertThat(Money.toCents(84.99)).isEqualTo(Money.of(84.99).toCents());
    }
}
