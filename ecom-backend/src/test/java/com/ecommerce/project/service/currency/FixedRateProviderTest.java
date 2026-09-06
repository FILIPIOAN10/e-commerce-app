package com.ecommerce.project.service.currency;

import com.ecommerce.project.config.CurrencyProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedRateProviderTest {

    private static CurrencyProperties props(String base, Map<String, String> rates) {
        CurrencyProperties p = new CurrencyProperties();
        p.setBase(base);
        java.util.Map<String, BigDecimal> parsed = new java.util.LinkedHashMap<>();
        rates.forEach((k, v) -> parsed.put(k, new BigDecimal(v)));
        p.setFixedRates(parsed);
        return p;
    }

    @Test
    void ratesIncludeTheBaseAtOneAndEveryConfiguredCurrency() {
        FixedRateProvider provider = new FixedRateProvider(
                props("USD", Map.of("EUR", "0.90", "jpy", "150")));

        Map<String, BigDecimal> rates = provider.ratesFor("USD");

        assertThat(rates.get("USD")).isEqualByComparingTo("1");
        assertThat(rates.get("EUR")).isEqualByComparingTo("0.90");
        assertThat(rates.get("JPY")).as("keys are upper-cased").isEqualByComparingTo("150");
    }

    @Test
    void onlySupportsTheConfiguredBase() {
        FixedRateProvider provider = new FixedRateProvider(props("USD", Map.of("EUR", "0.90")));

        assertThat(provider.supports("USD")).isTrue();
        assertThat(provider.supports("usd")).isTrue();
        assertThat(provider.supports("EUR")).isFalse();
    }

    @Test
    void refusesToQuoteAgainstANonBaseCurrency() {
        FixedRateProvider provider = new FixedRateProvider(props("USD", Map.of("EUR", "0.90")));

        assertThatThrownBy(() -> provider.ratesFor("EUR"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
