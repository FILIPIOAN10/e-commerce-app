package com.ecommerce.project.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * VAT / sales-tax configuration, bound from {@code app.tax.*}.
 *
 * <p>{@link #rates} maps an ISO 3166-1 alpha-2 country code (upper-case) to a
 * percentage; {@link #defaultRatePercent} is charged to any destination not
 * listed. {@link #enabled} set to {@code false} drops the tax line altogether —
 * for a store that prices VAT-inclusive, or one not registered to charge tax in
 * the destinations it ships to. {@link #taxableShipping} controls whether the
 * shipping charge is part of the taxable base (it is, under EU practice, so that
 * is the default in {@code application.properties}).
 *
 * <p>Unlike {@code ShippingCalculator}'s hard-coded bands this is
 * externalised, because a VAT rate is a legal figure that changes by
 * jurisdiction and by government budget, not an app constant.
 */
@ConfigurationProperties("app.tax")
public record TaxProperties(
        boolean enabled,
        BigDecimal defaultRatePercent,
        boolean taxableShipping,
        Map<String, BigDecimal> rates) {

    public TaxProperties {
        defaultRatePercent = defaultRatePercent == null ? BigDecimal.ZERO : defaultRatePercent;
        rates = rates == null ? Map.of() : rates;
    }
}
