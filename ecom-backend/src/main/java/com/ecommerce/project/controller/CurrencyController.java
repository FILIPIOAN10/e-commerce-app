package com.ecommerce.project.controller;

import com.ecommerce.project.payload.CurrencyDTO;
import com.ecommerce.project.service.currency.CurrencyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The currencies the storefront may offer in its picker. Read-only and public;
 * the list is short and cached, so this is a cheap call the SPA can make once on
 * load.
 */
@Tag(name = "Currency")
@RestController
@RequestMapping("/api")
public class CurrencyController extends BaseController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping("/public/currencies")
    public ResponseEntity<List<CurrencyDTO>> supportedCurrencies() {
        return ok(currencyService.activeCurrencies());
    }
}
