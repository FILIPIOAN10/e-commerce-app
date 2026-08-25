package com.ecommerce.project.service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic helpers. All monetary rounding is HALF_UP and operates on
 * the canonical decimal representation of a double to avoid floating-point
 * cent-drift (e.g. 84.99 * 100 != 8499 with raw double math).
 */
public final class Money {

    private Money() {
        // utility class
    }

    public static long toCents(double amount) {
        return BigDecimal.valueOf(amount)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }
}
