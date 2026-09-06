package com.ecommerce.project.service.pricing;

import com.ecommerce.project.config.TaxProperties;
import com.ecommerce.project.model.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaxRateResolver")
class TaxRateResolverTest {

    private static final Map<String, BigDecimal> RATES = Map.of(
            "RO", new BigDecimal("19"),
            "FR", new BigDecimal("20"));

    private TaxRateResolver resolver(boolean enabled, String defaultRate) {
        return new TaxRateResolver(new TaxProperties(enabled, new BigDecimal(defaultRate), true, RATES));
    }

    private Address country(String country) {
        return new Address(null, null, null, null, country, null);
    }

    @Test
    @DisplayName("matches a configured ISO code")
    void isoCode() {
        assertThat(resolver(true, "0").ratePercentFor(country("RO"))).isEqualByComparingTo("19");
        assertThat(resolver(true, "0").ratePercentFor(country("fr"))).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("maps the full English country name the address field actually stores")
    void fullName() {
        assertThat(resolver(true, "0").ratePercentFor(country("Romania"))).isEqualByComparingTo("19");
        assertThat(resolver(true, "0").ratePercentFor(country("  romania "))).isEqualByComparingTo("19");
    }

    @Test
    @DisplayName("an unknown or absent country falls back to the default rate")
    void fallsBackToDefault() {
        assertThat(resolver(true, "5").ratePercentFor(country("US"))).isEqualByComparingTo("5");
        assertThat(resolver(true, "5").ratePercentFor(country("Atlantis"))).isEqualByComparingTo("5");
        assertThat(resolver(true, "5").ratePercentFor(null)).isEqualByComparingTo("5");
        assertThat(resolver(true, "5").ratePercentFor(country("  "))).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("disabled: always zero, whatever the address")
    void disabled() {
        assertThat(resolver(false, "5").ratePercentFor(country("RO"))).isEqualByComparingTo("0");
    }
}
