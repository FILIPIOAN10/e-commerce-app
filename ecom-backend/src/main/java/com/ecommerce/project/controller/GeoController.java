package com.ecommerce.project.controller;

import com.ecommerce.project.service.geo.CityCatalog;
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
 * Reference geography for the address form.
 *
 * <p>Public because the guest checkout needs it before anyone has signed in, and
 * because it exposes nothing but a published ISO dataset — the same list is on
 * every address form on the internet.
 *
 * <p>Only cities live here. Countries and states are small enough (635 KB
 * together) to stay in the frontend bundle; cities are 7.9 MB and were the whole
 * reason the checkout chunk was 8.7 MB. See {@link CityCatalog}.
 */
@RestController
@RequestMapping("/api/public/geo")
@RequiredArgsConstructor
public class GeoController {

    private final CityCatalog cityCatalog;

    /**
     * Cities in one state, for the address form's third dropdown.
     *
     * <p>The response is immutable for the life of a deployment, so it carries a
     * long public cache header: the browser asks once per state per day, not once
     * per visit to checkout.
     *
     * @param country ISO 3166-1 alpha-2 country code, e.g. {@code RO}
     * @param state   that country's subdivision code, e.g. {@code CJ}
     */
    @GetMapping("/cities")
    public ResponseEntity<List<String>> cities(@RequestParam String country,
                                               @RequestParam String state) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(cityCatalog.citiesOf(country, state));
    }
}
