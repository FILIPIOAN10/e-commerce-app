package com.ecommerce.project.service.currency;

import com.ecommerce.project.config.CurrencyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live rates from the ECB's daily reference set, via the free, key-less
 * <a href="https://www.frankfurter.app">frankfurter.app</a>. Registered only
 * when {@code app.currency.provider=frankfurter}; ordered ahead of
 * {@link FixedRateProvider}, so the registry prefers it and falls back to the
 * fixed table if a call throws.
 *
 * <p>Not cached here — {@link ExchangeRateService} owns the Redis cache, so this
 * hits the network once per cache miss (about once an hour).
 */
@Slf4j
@Component
@Order(100)
@ConditionalOnProperty(prefix = "app.currency", name = "provider", havingValue = "frankfurter")
public class FrankfurterRateProvider implements ExchangeRateProvider {

    private final RestClient restClient;

    public FrankfurterRateProvider(CurrencyProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getFrankfurter().getBaseUrl())
                .build();
    }

    @Override
    public String name() {
        return "frankfurter";
    }

    @Override
    public boolean supports(String baseCurrency) {
        // frankfurter quotes any of its ~30 currencies as the base; we only ever
        // ask for the store base, and a bad response is handled by fall-through.
        return baseCurrency != null && baseCurrency.length() == 3;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, BigDecimal> ratesFor(String baseCurrency) {
        String base = baseCurrency.toUpperCase();
        FrankfurterResponse response = restClient.get()
                .uri(uri -> uri.path("/latest").queryParam("base", base).build())
                .retrieve()
                .body(FrankfurterResponse.class);

        if (response == null || response.rates() == null || response.rates().isEmpty()) {
            throw new IllegalStateException("frankfurter returned no rates for base " + base);
        }

        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put(base, BigDecimal.ONE);
        response.rates().forEach((code, rate) -> rates.put(code.toUpperCase(), rate));
        log.debug("frankfurter supplied {} rates against {}", rates.size(), base);
        return rates;
    }

    /** The slice of the frankfurter response we read. */
    record FrankfurterResponse(String base, String date, Map<String, BigDecimal> rates) {
    }
}
