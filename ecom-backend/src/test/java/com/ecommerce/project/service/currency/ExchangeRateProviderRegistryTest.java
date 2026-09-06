package com.ecommerce.project.service.currency;

import com.ecommerce.project.exception.APIException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRateProviderRegistryTest {

    /** A provider that either returns a fixed map or blows up, on command. */
    private static ExchangeRateProvider provider(String name, boolean supports,
                                                 Map<String, BigDecimal> result, RuntimeException failure) {
        return new ExchangeRateProvider() {
            @Override public String name() { return name; }
            @Override public boolean supports(String baseCurrency) { return supports; }
            @Override public Map<String, BigDecimal> ratesFor(String baseCurrency) {
                if (failure != null) throw failure;
                return result;
            }
        };
    }

    @Test
    void usesTheFirstSupportingProviderThatSucceeds() {
        var live = provider("live", true, Map.of("EUR", new BigDecimal("0.93")), null);
        var fixed = provider("fixed", true, Map.of("EUR", new BigDecimal("0.90")), null);

        Map<String, BigDecimal> rates = new ExchangeRateProviderRegistry(List.of(live, fixed)).ratesFor("USD");

        assertThat(rates.get("EUR")).isEqualByComparingTo("0.93");
    }

    @Test
    void fallsThroughToTheNextProviderWhenOneThrows() {
        var live = provider("live", true, null, new IllegalStateException("api down"));
        var fixed = provider("fixed", true, Map.of("EUR", new BigDecimal("0.90")), null);

        Map<String, BigDecimal> rates = new ExchangeRateProviderRegistry(List.of(live, fixed)).ratesFor("USD");

        assertThat(rates.get("EUR")).isEqualByComparingTo("0.90");
    }

    @Test
    void skipsProvidersThatDoNotSupportTheBase() {
        var eurOnly = provider("eur-only", false, null, null);
        var fixed = provider("fixed", true, Map.of("EUR", new BigDecimal("0.90")), null);

        assertThat(new ExchangeRateProviderRegistry(List.of(eurOnly, fixed)).ratesFor("USD").get("EUR"))
                .isEqualByComparingTo("0.90");
    }

    @Test
    void throwsWhenNoProviderCanAnswer() {
        var live = provider("live", true, null, new IllegalStateException("api down"));

        assertThatThrownBy(() -> new ExchangeRateProviderRegistry(List.of(live)).ratesFor("USD"))
                .isInstanceOf(APIException.class);
    }
}
