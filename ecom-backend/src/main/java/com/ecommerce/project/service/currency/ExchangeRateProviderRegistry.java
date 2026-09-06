package com.ecommerce.project.service.currency;

import com.ecommerce.project.exception.APIException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Routes a rate lookup to the first {@link ExchangeRateProvider} that supports
 * the base currency, falling through to the next when one throws. Spring injects
 * every provider bean already sorted by {@code @Order}, so registering a source
 * is a new {@code @Component} and nothing here changes.
 *
 * <p>{@code FixedRateProvider} is always present and always supports the base, so
 * {@link #ratesFor} only reaches its final {@code throw} if that provider itself
 * is misconfigured.
 */
@Slf4j
@Component
public class ExchangeRateProviderRegistry {

    private final List<ExchangeRateProvider> providers;

    public ExchangeRateProviderRegistry(List<ExchangeRateProvider> providers) {
        this.providers = List.copyOf(providers);
        log.info("Exchange-rate providers, in order: {}",
                providers.stream().map(ExchangeRateProvider::name).toList());
    }

    public Map<String, BigDecimal> ratesFor(String baseCurrency) {
        for (ExchangeRateProvider provider : providers) {
            if (!provider.supports(baseCurrency)) {
                continue;
            }
            try {
                return provider.ratesFor(baseCurrency);
            } catch (Exception e) {
                log.warn("Exchange-rate provider '{}' failed for base {}: {} — trying the next",
                        provider.name(), baseCurrency, e.getMessage());
            }
        }
        throw new APIException("No exchange-rate provider could supply rates for " + baseCurrency);
    }
}
