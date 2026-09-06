package com.ecommerce.project.service.currency;

import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Restates product prices in the currency the shopper is browsing in. Runs at
 * the controller boundary, <em>after</em> the {@code @Cacheable} product service
 * has returned — so the USD figures are cached once and conversion is a cheap
 * multiply per response. Product-cache entries stay single-currency.
 *
 * <p>Mutates the DTOs in place. That is safe because the product caches are
 * Redis-backed and hand back a freshly deserialized copy on every read; do not
 * point those caches at an in-process store without revisiting this.
 */
@Component
@RequiredArgsConstructor
public class ProductPriceConverter {

    private final CurrencyService currencyService;

    /** Convert every product in {@code response} into {@code requestedCurrency} (no-op for the base or a blank/unknown code). */
    public void applyCurrency(ProductResponse response, String requestedCurrency) {
        if (response == null || response.getContent() == null) {
            return;
        }
        String code = currencyService.resolveForBrowsing(requestedCurrency);
        if (currencyService.isBase(code)) {
            return;
        }
        BigDecimal rate = currencyService.rateFor(code);
        int digits = currencyService.decimalDigitsFor(code);
        response.getContent().forEach(product -> restate(product, code, rate, digits));
    }

    /** Convert a single product into {@code requestedCurrency} (no-op for the base or a blank/unknown code). */
    public void applyCurrency(ProductDTO product, String requestedCurrency) {
        if (product == null) {
            return;
        }
        String code = currencyService.resolveForBrowsing(requestedCurrency);
        if (currencyService.isBase(code)) {
            return;
        }
        restate(product, code, currencyService.rateFor(code), currencyService.decimalDigitsFor(code));
    }

    private void restate(ProductDTO product, String code, BigDecimal rate, int digits) {
        product.setPrice(scale(product.getPrice(), rate, digits));
        product.setSpecialPrice(scale(product.getSpecialPrice(), rate, digits));
        product.setCurrency(code);
    }

    private static BigDecimal scale(BigDecimal usd, BigDecimal rate, int digits) {
        return usd == null ? null : usd.multiply(rate).setScale(digits, RoundingMode.HALF_UP);
    }
}
