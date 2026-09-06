package com.ecommerce.project.service.currency;

import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductPriceConverterTest {

    @Mock private CurrencyService currencyService;
    @InjectMocks private ProductPriceConverter converter;

    private static ProductDTO product(String price, String specialPrice) {
        ProductDTO dto = new ProductDTO();
        dto.setPrice(new BigDecimal(price));
        dto.setSpecialPrice(specialPrice == null ? null : new BigDecimal(specialPrice));
        return dto;
    }

    @Test
    void convertsEveryProductInAResponseAndStampsTheCurrency() {
        when(currencyService.resolveForBrowsing("EUR")).thenReturn("EUR");
        when(currencyService.isBase("EUR")).thenReturn(false);
        when(currencyService.rateFor("EUR")).thenReturn(new BigDecimal("0.90"));
        when(currencyService.decimalDigitsFor("EUR")).thenReturn(2);

        ProductResponse response = new ProductResponse();
        response.setContent(List.of(product("100.00", "80.00"), product("19.99", null)));

        converter.applyCurrency(response, "EUR");

        assertThat(response.getContent().get(0).getPrice()).isEqualByComparingTo("90.00");
        assertThat(response.getContent().get(0).getSpecialPrice()).isEqualByComparingTo("72.00");
        assertThat(response.getContent().get(0).getCurrency()).isEqualTo("EUR");
        assertThat(response.getContent().get(1).getSpecialPrice()).as("null stays null").isNull();
        assertThat(response.getContent().get(1).getCurrency()).isEqualTo("EUR");
    }

    @Test
    void isANoOpForTheBaseCurrency() {
        when(currencyService.resolveForBrowsing(null)).thenReturn("USD");
        when(currencyService.isBase("USD")).thenReturn(true);

        ProductDTO dto = product("100.00", "80.00");
        converter.applyCurrency(dto, null);

        assertThat(dto.getPrice()).isEqualByComparingTo("100.00");
        assertThat(dto.getCurrency()).as("left as USD/unset").isNull();
    }

    @Test
    void roundsToTheCurrencysPrecision() {
        when(currencyService.resolveForBrowsing("JPY")).thenReturn("JPY");
        when(currencyService.isBase("JPY")).thenReturn(false);
        when(currencyService.rateFor("JPY")).thenReturn(new BigDecimal("149.55"));
        when(currencyService.decimalDigitsFor("JPY")).thenReturn(0);

        ProductDTO dto = product("19.99", null);
        converter.applyCurrency(dto, "JPY");

        assertThat(dto.getPrice()).isEqualByComparingTo("2990");   // 19.99 * 149.55 = 2989.5045 -> 2990 at 0 dp
        assertThat(dto.getPrice().scale()).isZero();
    }
}
