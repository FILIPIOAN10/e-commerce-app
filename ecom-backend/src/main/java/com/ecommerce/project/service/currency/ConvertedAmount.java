package com.ecommerce.project.service.currency;

import java.math.BigDecimal;

/**
 * A base-currency (USD) amount expressed in a presentation currency: the
 * converted {@code amount}, the {@code currency} it is in, and the {@code rate}
 * used (units of {@code currency} per one USD). The rate travels with the amount
 * so a caller — an order record, an invoice line — can store it and reproduce
 * the figure later.
 */
public record ConvertedAmount(BigDecimal amount, String currency, BigDecimal rate) {
}
