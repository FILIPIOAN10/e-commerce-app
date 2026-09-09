package com.ecommerce.project.controller;

import com.ecommerce.project.payload.CurrencyDTO;
import com.ecommerce.project.service.currency.CurrencyService;
import com.ecommerce.project.service.geo.CityCatalog;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Public read-only reference data the storefront needs before anyone has signed
 * in: the currencies the picker may offer, and the cities behind the address
 * form's third dropdown. Both are short, cacheable, and expose nothing but
 * published lists.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicReferenceController extends BaseController {

    private final CurrencyService currencyService;
    private final CityCatalog cityCatalog;

    /**
     * The currencies the storefront may offer in its picker. The list is short
     * and cached, so this is a cheap call the SPA can make once on load.
     */
    @Tag(name = "Currency")
    @GetMapping("/currencies")
    public ResponseEntity<List<CurrencyDTO>> supportedCurrencies() {
        return ok(currencyService.activeCurrencies());
    }

    /**
     * Cities in one state, for the address form's third dropdown.
     *
     * <p>Only cities are served here. Countries and states are small enough
     * (635 KB together) to stay in the frontend bundle; cities are 7.9 MB and
     * were the whole reason the checkout chunk was 8.7 MB. See {@link CityCatalog}.
     *
     * <p>The response is immutable for the life of a deployment, so it carries a
     * long public cache header: the browser asks once per state per day, not once
     * per visit to checkout.
     *
     * @param country ISO 3166-1 alpha-2 country code, e.g. {@code RO}
     * @param state   that country's subdivision code, e.g. {@code CJ}
     */
    @Tag(name = "Geo")
    @GetMapping("/geo/cities")
    public ResponseEntity<List<String>> cities(@RequestParam String country,
                                               @RequestParam String state) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(cityCatalog.citiesOf(country, state));
    }
}
