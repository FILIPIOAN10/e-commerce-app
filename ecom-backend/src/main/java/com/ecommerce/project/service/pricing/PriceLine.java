package com.ecommerce.project.service.pricing;

import java.math.BigDecimal;

/**
 * One entry in a {@link PriceBreakdown}: a labelled adjustment to the running
 * total. {@code amount} is signed — negative for a discount, positive for a
 * charge such as shipping or tax — so the running total is just the subtotal
 * plus the sum of every line.
 */
public record PriceLine(String label, PriceLineType type, BigDecimal amount) {
}
