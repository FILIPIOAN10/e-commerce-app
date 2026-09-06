package com.ecommerce.project.service.currency;

import com.ecommerce.project.config.CurrencyProperties;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.SupportedCurrency;
import com.ecommerce.project.repository.SupportedCurrencyRepository;
import com.ecommerce.project.service.pricing.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock private SupportedCurrencyRepository currencyRepository;
    @Mock private ExchangeRateService exchangeRateService;

    private CurrencyService currencyService;

    private static SupportedCurrency currency(String code, int digits, int order) {
        SupportedCurrency c = new SupportedCurrency();
        c.setCode(code);
        c.setSymbol(code);
        c.setDecimalDigits((short) digits);
        c.setActive(true);
        c.setSortOrder(order);
        return c;
    }

    @BeforeEach
    void setUp() {
        CurrencyProperties properties = new CurrencyProperties(); // base USD
        currencyService = new CurrencyService(currencyRepository, exchangeRateService, properties);
        when(currencyRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(
                currency("USD", 2, 0), currency("EUR", 2, 1), currency("JPY", 0, 2)));
    }

    @Test
    void resolveForBrowsing_isLenient() {
        assertThat(currencyService.resolveForBrowsing(null)).isEqualTo("USD");
        assertThat(currencyService.resolveForBrowsing("  ")).isEqualTo("USD");
        assertThat(currencyService.resolveForBrowsing("eur")).isEqualTo("EUR");
        assertThat(currencyService.resolveForBrowsing("XYZ")).as("unknown → base").isEqualTo("USD");
    }

    @Test
    void requireSupported_isStrictButAlwaysAcceptsBlankAndBase() {
        assertThat(currencyService.requireSupported(null)).isEqualTo("USD");
        assertThat(currencyService.requireSupported("USD")).isEqualTo("USD");
        assertThat(currencyService.requireSupported("eur")).isEqualTo("EUR");
        assertThatThrownBy(() -> currencyService.requireSupported("XYZ"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void convert_roundsToTheTargetCurrencysOwnPrecision() {
        when(exchangeRateService.rateFor("EUR")).thenReturn(new BigDecimal("0.9137"));
        when(exchangeRateService.rateFor("JPY")).thenReturn(new BigDecimal("149.55"));

        ConvertedAmount eur = currencyService.convert(Money.of(new BigDecimal("84.99")), "EUR");
        assertThat(eur.currency()).isEqualTo("EUR");
        assertThat(eur.rate()).isEqualByComparingTo("0.9137");
        assertThat(eur.amount()).isEqualByComparingTo("77.66");   // 84.99 * 0.9137 = 77.655..., 2 dp

        ConvertedAmount jpy = currencyService.convert(Money.of(new BigDecimal("84.99")), "JPY");
        assertThat(jpy.amount()).as("JPY has no minor unit").isEqualByComparingTo("12710");
        assertThat(jpy.amount().scale()).isEqualTo(0);
    }

    @Test
    void convert_toBaseIsIdentityAndNeedsNoRate() {
        when(exchangeRateService.rateFor("USD")).thenReturn(BigDecimal.ONE);

        ConvertedAmount usd = currencyService.convert(Money.of(new BigDecimal("19.99")), "USD");

        assertThat(usd.amount()).isEqualByComparingTo("19.99");
        assertThat(usd.currency()).isEqualTo("USD");
    }

    @Test
    void activeCurrencies_areFlatDtosWithTheBaseFlagged() {
        assertThat(currencyService.activeCurrencies())
                .anySatisfy(c -> {
                    assertThat(c.code()).isEqualTo("USD");
                    assertThat(c.base()).isTrue();
                })
                .anySatisfy(c -> {
                    assertThat(c.code()).isEqualTo("JPY");
                    assertThat(c.decimalDigits()).isZero();
                    assertThat(c.base()).isFalse();
                });
    }
}
