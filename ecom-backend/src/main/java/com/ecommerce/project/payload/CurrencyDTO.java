package com.ecommerce.project.payload;

/**
 * A currency the store offers, as returned by {@code GET /api/public/currencies}
 * and cached (so it is a flat DTO, not the {@code SupportedCurrency} entity).
 *
 * @param code          ISO 4217, e.g. {@code EUR}
 * @param symbol        display symbol, e.g. {@code €}
 * @param decimalDigits minor-unit digits (2 for most, 0 for JPY)
 * @param base          whether this is the store's settlement currency (USD)
 */
public record CurrencyDTO(String code, String symbol, int decimalDigits, boolean base) {
}
