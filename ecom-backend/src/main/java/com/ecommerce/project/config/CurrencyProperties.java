package com.ecommerce.project.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Multi-currency configuration, bound from {@code app.currency.*}.
 *
 * <p>{@link #base} is the settlement currency every price is stored in (USD).
 * {@link #provider} selects the live rate source — {@code fixed} uses
 * {@link #fixedRates} only; {@code frankfurter} adds the ECB feed at
 * {@link Frankfurter#baseUrl} and falls back to {@link #fixedRates} when it is
 * unreachable. {@link #fixedRates} maps an ISO 4217 code to units of that
 * currency per one unit of {@link #base}; it is both the offline provider and
 * the floor under the live one, so keep it roughly current.
 */
@ConfigurationProperties("app.currency")
public class CurrencyProperties {

    private String base = "USD";
    private String provider = "fixed";
    private Map<String, BigDecimal> fixedRates = new LinkedHashMap<>();
    private Frankfurter frankfurter = new Frankfurter();

    public String getBase() {
        return base == null ? "USD" : base.toUpperCase();
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getProvider() {
        return provider == null ? "fixed" : provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Map<String, BigDecimal> getFixedRates() {
        return fixedRates;
    }

    public void setFixedRates(Map<String, BigDecimal> fixedRates) {
        this.fixedRates = fixedRates == null ? new LinkedHashMap<>() : fixedRates;
    }

    public Frankfurter getFrankfurter() {
        return frankfurter;
    }

    public void setFrankfurter(Frankfurter frankfurter) {
        this.frankfurter = frankfurter == null ? new Frankfurter() : frankfurter;
    }

    public static class Frankfurter {
        private String baseUrl = "https://api.frankfurter.app";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
