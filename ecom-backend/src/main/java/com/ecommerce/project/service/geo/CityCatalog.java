package com.ecommerce.project.service.geo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * City names for a (country, state) pair, read from a bundled dataset.
 *
 * <p>This exists so the browser does not have to. The checkout address form used
 * to resolve cities from the {@code country-state-city} npm package, whose city
 * dataset is 7.9 MB — 92% of an 8.7 MB lazy chunk (2.3 MB gzipped) that every
 * customer downloaded the moment they opened the address form. That is a
 * multi-second stall on a phone, at the single highest-value moment in the app.
 * Countries and states together are only 635 KB, so they stay client-side; only
 * the cities moved here.
 *
 * <p>The bundled file is a reshaped copy of that package's {@code city.json}:
 * grouped by {@code "<countryCode>-<stateCode>"}, names only, deduplicated and
 * sorted. Dropping the coordinates and the repeated country/state codes takes it
 * from 7.7 MB to 2.0 MB. To regenerate it after a package upgrade, see
 * {@code scripts/generate-city-index.py}.
 *
 * <p>Parsed on first use rather than at startup: 2 MB of JSON is ~200ms that a
 * deployment should not pay when most requests never touch an address form. The
 * holder is volatile and the parse is idempotent, so a race between two first
 * callers costs one wasted parse and never a torn read.
 */
@Slf4j
@Component
public class CityCatalog {

    private static final String RESOURCE = "geo/cities-by-state.json";

    private volatile Map<String, List<String>> citiesByState;

    /**
     * @param countryCode ISO 3166-1 alpha-2, e.g. {@code RO}
     * @param stateCode   the country's own subdivision code, e.g. {@code CJ}
     * @return the state's cities, sorted; empty when the pair is unknown, which
     *         is a legitimate answer — plenty of territories have no subdivision
     *         entries at all, and a caller asking for one is not making an error
     */
    public List<String> citiesOf(String countryCode, String stateCode) {
        if (countryCode == null || countryCode.isBlank() || stateCode == null || stateCode.isBlank()) {
            return List.of();
        }
        String key = countryCode.trim().toUpperCase() + "-" + stateCode.trim().toUpperCase();
        return index().getOrDefault(key, List.of());
    }

    private Map<String, List<String>> index() {
        Map<String, List<String>> local = citiesByState;
        if (local == null) {
            synchronized (this) {
                local = citiesByState;
                if (local == null) {
                    local = load();
                    citiesByState = local;
                }
            }
        }
        return local;
    }

    private Map<String, List<String>> load() {
        long startedAt = System.currentTimeMillis();
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, List<String>> parsed = new ObjectMapper()
                    .readValue(in, new TypeReference<Map<String, List<String>>>() {});
            log.info("Loaded city catalogue: {} states, {} cities, in {}ms",
                    parsed.size(),
                    parsed.values().stream().mapToInt(List::size).sum(),
                    System.currentTimeMillis() - startedAt);
            return Map.copyOf(parsed);
        } catch (IOException e) {
            // The file ships inside the jar, so this is a packaging fault rather
            // than anything a request did. Fail loudly instead of degrading to an
            // empty catalogue, which would look like "this state has no cities"
            // for every state.
            throw new UncheckedIOException("Could not read " + RESOURCE + " from the classpath", e);
        }
    }
}
