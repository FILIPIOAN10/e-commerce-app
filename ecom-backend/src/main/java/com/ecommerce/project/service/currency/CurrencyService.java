package com.ecommerce.project.service.currency;

import com.ecommerce.project.config.CurrencyProperties;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.payload.CurrencyDTO;
import com.ecommerce.project.repository.SupportedCurrencyRepository;
import com.ecommerce.project.service.pricing.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The presentation-currency boundary. Everything priced in the app is USD; this
 * validates a currency the customer asked for and converts a USD {@link Money}
 * into it, rounded to that currency's own minor-unit precision (2 for USD/EUR,
 * 0 for JPY).
 *
 * <p>Two resolution modes on purpose: {@link #resolveForBrowsing(String)} is
 * lenient — an unknown {@code X-Currency} header on a product list falls back to
 * the base rather than 500-ing a page — while {@link #requireSupported(String)}
 * is strict, for checkout, where quoting a currency we do not actually support
 * would be a promise we cannot keep.
 */
@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final SupportedCurrencyRepository currencyRepository;
    private final ExchangeRateService exchangeRateService;
    private final CurrencyProperties properties;

    /** Active currencies, as flat DTOs (cached — never the entity). Base first by {@code sort_order}. */
    @Cacheable("supportedCurrencies")
    public List<CurrencyDTO> activeCurrencies() {
        String base = properties.getBase();
        return currencyRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(c -> new CurrencyDTO(c.getCode(), c.getSymbol(), c.getDecimalDigits(),
                        c.getCode().equals(base)))
                .toList();
    }

    public String baseCurrency() {
        return properties.getBase();
    }

    /** Units of {@code currency} per one USD (1 for the base). Reads the Redis-cached rate table. */
    public BigDecimal rateFor(String currency) {
        return exchangeRateService.rateFor(requireSupported(currency));
    }

    /** Minor-unit digits for {@code currency} (2 for most, 0 for JPY); 2 if unknown. */
    public int decimalDigitsFor(String currency) {
        return find(currency.toUpperCase()).map(CurrencyDTO::decimalDigits).orElse(2);
    }

    public boolean isBase(String currency) {
        return baseCurrency().equalsIgnoreCase(currency);
    }

    /** Lenient: blank or unknown → the store base. Never throws. */
    public String resolveForBrowsing(String requested) {
        if (requested == null || requested.isBlank()) {
            return baseCurrency();
        }
        String code = requested.trim().toUpperCase();
        if (code.equals(baseCurrency())) {
            return code;
        }
        return find(code).map(CurrencyDTO::code).orElse(baseCurrency());
    }

    /** Strict: blank → base; anything not active-and-supported is rejected. The base is always accepted. */
    public String requireSupported(String requested) {
        if (requested == null || requested.isBlank()) {
            return baseCurrency();
        }
        String code = requested.trim().toUpperCase();
        if (code.equals(baseCurrency())) {
            return code;
        }
        return find(code).map(CurrencyDTO::code)
                .orElseThrow(() -> new APIException("Unsupported currency: " + requested));
    }

    /**
     * {@code base} (a USD amount) expressed in {@code targetCurrency}. The rate
     * used is returned alongside so the caller can freeze it onto an order.
     */
    public ConvertedAmount convert(Money base, String targetCurrency) {
        String code = requireSupported(targetCurrency);
        BigDecimal rate = exchangeRateService.rateFor(code);
        int digits = find(code).map(CurrencyDTO::decimalDigits).orElse(2);
        BigDecimal amount = base.toBigDecimal().multiply(rate).setScale(digits, RoundingMode.HALF_UP);
        return new ConvertedAmount(amount, code, rate);
    }

    private Optional<CurrencyDTO> find(String code) {
        return activeCurrencies().stream().filter(c -> c.code().equals(code)).findFirst();
    }
}
