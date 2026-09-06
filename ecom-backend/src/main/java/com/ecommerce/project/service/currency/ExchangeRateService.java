package com.ecommerce.project.service.currency;

import com.ecommerce.project.config.CurrencyProperties;
import com.ecommerce.project.exception.APIException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * The store's view of "what is one USD worth in X". Wraps
 * {@link ExchangeRateProviderRegistry} in a Redis cache keyed by base currency
 * ({@code exchangeRates} cache, ~1h TTL): a rate table is fetched once per hour
 * and every conversion in that window reads it from Redis. TTL is the whole
 * invalidation strategy here — rates drift slowly and a stale-by-an-hour rate is
 * acceptable where a per-request network call is not.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateProviderRegistry registry;
    private final CurrencyProperties properties;

    /**
     * Units of each quote currency per one unit of {@code baseCurrency}. A plain
     * {@link HashMap} so the value round-trips through the JSON cache cleanly.
     */
    @Cacheable(value = "exchangeRates", key = "#baseCurrency")
    public Map<String, BigDecimal> rates(String baseCurrency) {
        return new HashMap<>(registry.ratesFor(baseCurrency.toUpperCase()));
    }

    /** Rate table against the configured store base (USD). */
    public Map<String, BigDecimal> baseRates() {
        return rates(properties.getBase());
    }

    /** One USD-based rate, or {@code 1} for the base itself. */
    public BigDecimal rateFor(String quoteCurrency) {
        String quote = quoteCurrency.toUpperCase();
        if (properties.getBase().equals(quote)) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = baseRates().get(quote);
        if (rate == null || rate.signum() <= 0) {
            throw new APIException("No exchange rate available for " + quote);
        }
        return rate;
    }
}
