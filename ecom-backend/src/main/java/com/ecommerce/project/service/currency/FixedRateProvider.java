package com.ecommerce.project.service.currency;

import com.ecommerce.project.config.CurrencyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rates straight from {@code app.currency.fixed-rates}. Always last
 * ({@code @Order} highest) and always {@link #supports supports} the base, so it
 * is the guaranteed fallback when a live provider is off or unreachable. Keeping
 * a hand-maintained table here means the store still quotes sane prices with no
 * network at all — the trade-off is that those numbers are only as fresh as the
 * last time someone edited the config.
 */
@Component
@Order(1000)
@RequiredArgsConstructor
public class FixedRateProvider implements ExchangeRateProvider {

    private final CurrencyProperties properties;

    @Override
    public String name() {
        return "fixed";
    }

    @Override
    public boolean supports(String baseCurrency) {
        return properties.getBase().equalsIgnoreCase(baseCurrency);
    }

    @Override
    public Map<String, BigDecimal> ratesFor(String baseCurrency) {
        if (!supports(baseCurrency)) {
            throw new IllegalArgumentException(
                    "fixed rates are configured against " + properties.getBase() + ", not " + baseCurrency);
        }
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put(properties.getBase().toUpperCase(), BigDecimal.ONE);
        properties.getFixedRates().forEach((code, rate) -> rates.put(code.toUpperCase(), rate));
        return rates;
    }
}
