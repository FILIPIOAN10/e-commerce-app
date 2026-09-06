package com.ecommerce.project.service.pricing;

import com.ecommerce.project.config.TaxProperties;
import com.ecommerce.project.model.Address;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Resolves the VAT rate that applies to a delivery address, from
 * {@link TaxProperties}.
 *
 * <p>Addresses in this app store the country as a free-text field — the seed
 * data and {@code ShippingCalculator} both deal in {@code "Romania"} as often as
 * {@code "RO"} — so the lookup normalises a handful of full English names to
 * their ISO code before hitting the configured map. Anything it cannot place
 * (an unknown country, a null address, tax disabled) falls back to
 * {@link TaxProperties#defaultRatePercent()}.
 */
@Component
public class TaxRateResolver {

    /** Full names the address field is known to carry, mapped to the ISO code the config keys use. */
    private static final Map<String, String> NAME_TO_ISO = Map.of(
            "ROMANIA", "RO",
            "GERMANY", "DE",
            "FRANCE", "FR",
            "UNITED KINGDOM", "GB",
            "UNITED STATES", "US",
            "UNITED STATES OF AMERICA", "US");

    private final TaxProperties properties;

    public TaxRateResolver(TaxProperties properties) {
        this.properties = properties;
    }

    /** Whether the shipping charge is part of the taxable base. */
    public boolean taxableShipping() {
        return properties.taxableShipping();
    }

    /**
     * The VAT percentage for {@code address} — e.g. {@code 19} for a 19% rate.
     * {@code BigDecimal.ZERO} when tax is disabled or nothing charges here, in
     * which case {@link VatRule} adds no line at all.
     */
    public BigDecimal ratePercentFor(Address address) {
        if (!properties.enabled()) {
            return BigDecimal.ZERO;
        }
        String code = isoCode(address);
        if (code == null) {
            return properties.defaultRatePercent();
        }
        return properties.rates().getOrDefault(code, properties.defaultRatePercent());
    }

    private String isoCode(Address address) {
        if (address == null || address.getCountry() == null || address.getCountry().isBlank()) {
            return null;
        }
        String country = address.getCountry().trim().toUpperCase();
        if (country.length() == 2) {
            return country;
        }
        return NAME_TO_ISO.get(country);
    }
}
