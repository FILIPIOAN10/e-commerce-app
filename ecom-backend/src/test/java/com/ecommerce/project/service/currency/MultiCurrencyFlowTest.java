package com.ecommerce.project.service.currency;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.SupportedCurrency;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.CurrencyDTO;
import com.ecommerce.project.payload.GuestCheckoutRequestDTO;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.SupportedCurrencyRepository;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.pricing.Money;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Multi-currency, end to end against real Postgres + Redis: the currency list is
 * served from the DB through a cache, a checkout freezes the chosen currency and
 * its USD rate onto the order while the money columns stay in USD, and an
 * unsupported currency is refused.
 *
 * <p>The shared test profile disables Flyway, so {@code V32}'s seed does not run;
 * this class seeds {@code supported_currencies} itself. Rates come from the
 * pinned {@code app.currency.fixed-rates.*} in {@code application-test.properties}
 * (EUR 0.90, JPY 150).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MultiCurrencyFlowTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private SupportedCurrencyRepository currencyRepository;
    @Autowired private CurrencyService currencyService;
    @Autowired private ExchangeRateService exchangeRateService;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private CacheManager cacheManager;

    private final String tag = "mcx" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;
    private Long productId;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(txManager);
        evictCurrencyCaches();

        tx.executeWithoutResult(status -> {
            for (String[] c : new String[][]{{"USD", "$", "2", "0"}, {"EUR", "€", "2", "1"}, {"JPY", "¥", "0", "2"}}) {
                if (!currencyRepository.existsById(c[0])) {
                    SupportedCurrency sc = new SupportedCurrency();
                    sc.setCode(c[0]);
                    sc.setSymbol(c[1]);
                    sc.setDecimalDigits(Short.parseShort(c[2]));
                    sc.setActive(true);
                    sc.setSortOrder(Integer.parseInt(c[3]));
                    entityManager.persist(sc);
                }
            }

            Category category = new Category();
            category.setCategoryName(tag + "-cat");
            entityManager.persist(category);

            Product product = new Product();
            product.setProductName(tag + "-widget");
            product.setDescription("currency fixture");
            product.setPrice(new BigDecimal("100.00"));
            product.setSpecialPrice(new BigDecimal("100.00"));
            product.setDiscount(new BigDecimal("0.0"));
            product.setQuantity(50);
            product.setCategory(category);
            entityManager.persist(product);
            productId = product.getProductId();
        });
        evictCurrencyCaches();
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            tagged("DELETE FROM order_items WHERE product_id IN "
                   + "(SELECT product_id FROM products WHERE product_name LIKE :t)");
            tagged("DELETE FROM invoices WHERE order_id IN (SELECT id FROM orders WHERE email LIKE :t)");
            tagged("DELETE FROM orders WHERE email LIKE :t");
            tagged("DELETE FROM stock_movement WHERE product_id IN "
                   + "(SELECT product_id FROM products WHERE product_name LIKE :t)");
            tagged("DELETE FROM products WHERE product_name LIKE :t");
            tagged("DELETE FROM categories WHERE category_name LIKE :t");
            entityManager.createNativeQuery("DELETE FROM addresses WHERE street = :s")
                    .setParameter("s", tag + " Street").executeUpdate();
        });
        evictCurrencyCaches();
    }

    @Test
    @DisplayName("the currency list is the active set, base first and flagged")
    void currencyListExposesTheActiveSet() {
        List<CurrencyDTO> currencies = currencyService.activeCurrencies();

        assertThat(currencies).extracting(CurrencyDTO::code).contains("USD", "EUR", "JPY");
        assertThat(currencies).filteredOn(CurrencyDTO::base).extracting(CurrencyDTO::code).containsExactly("USD");
        assertThat(currencies).filteredOn(c -> c.code().equals("JPY")).singleElement()
                .satisfies(jpy -> assertThat(jpy.decimalDigits()).isZero());
    }

    @Test
    @DisplayName("rates load from the fixed provider, base is always 1")
    void ratesComeFromTheFixedProvider() {
        assertThat(exchangeRateService.rateFor("EUR")).isEqualByComparingTo("0.90");
        assertThat(exchangeRateService.rateFor("JPY")).isEqualByComparingTo("150");
        assertThat(exchangeRateService.rateFor("USD")).as("base is always 1").isEqualByComparingTo("1");

        // rate table for the base, also what the Redis-backed exchangeRates cache holds
        assertThat(exchangeRateService.rates("USD").get("EUR")).isEqualByComparingTo("0.90");
    }

    @Test
    @DisplayName("a EUR checkout freezes currency + rate on the order; USD amounts are unchanged")
    void checkoutInEuroFreezesTheRate() {
        OrderDTO usd = placeGuestOrder(1, null);
        OrderDTO eur = placeGuestOrder(1, "EUR");

        assertThat(eur.getCurrencyCode()).isEqualTo("EUR");
        assertThat(eur.getExchangeRate()).isEqualByComparingTo("0.90");
        assertThat(usd.getCurrencyCode()).isEqualTo("USD");
        assertThat(usd.getExchangeRate()).isEqualByComparingTo("1");

        assertThat(eur.getTotalAmount())
                .as("settlement currency is still USD — the amount does not change")
                .isEqualByComparingTo(usd.getTotalAmount());

        Order reloaded = tx.execute(s -> {
            entityManager.clear();
            return orderRepository.findByIdWithDetails(eur.getOrderId()).orElseThrow();
        });
        assertThat(reloaded.getCurrencyCode()).isEqualTo("EUR");
        assertThat(reloaded.getExchangeRate()).isEqualByComparingTo("0.90");
        assertThat(reloaded.getExchangeRate().scale()).isGreaterThan(0);
        assertThat(columnType("orders", "exchange_rate")).isEqualTo("numeric");
    }

    @Test
    @DisplayName("an unsupported currency is rejected at checkout")
    void unsupportedCurrencyIsRefused() {
        assertThatThrownBy(() -> placeGuestOrder(1, "XYZ"))
                .isInstanceOf(APIException.class);
    }

    @Test
    @DisplayName("convert applies the rate and the target currency's precision")
    void convertUsesRateAndPrecision() {
        ConvertedAmount eur = currencyService.convert(Money.of(new BigDecimal("100.00")), "EUR");
        assertThat(eur.amount()).isEqualByComparingTo("90.00");
        assertThat(eur.rate()).isEqualByComparingTo("0.90");

        ConvertedAmount jpy = currencyService.convert(Money.of(new BigDecimal("100.00")), "JPY");
        assertThat(jpy.amount()).isEqualByComparingTo("15000");
        assertThat(jpy.amount().scale()).isZero();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void evictCurrencyCaches() {
        cacheManager.getCache("supportedCurrencies").clear();
        cacheManager.getCache("exchangeRates").clear();
    }

    private String columnType(String table, String column) {
        return tx.execute(status -> (String) entityManager
                .createNativeQuery("SELECT data_type FROM information_schema.columns "
                                   + "WHERE table_name = :t AND column_name = :c")
                .setParameter("t", table).setParameter("c", column).getSingleResult());
    }

    private void tagged(String sql) {
        entityManager.createNativeQuery(sql).setParameter("t", tag + "%").executeUpdate();
    }

    private OrderDTO placeGuestOrder(int quantity, String currencyCode) {
        GuestCheckoutRequestDTO request = new GuestCheckoutRequestDTO();
        request.setEmail(tag + "@example.com");
        request.setPaymentMethod("CASH");
        request.setCouponCodes(List.of());
        request.setCurrencyCode(currencyCode);
        request.setAddress(new AddressDTO(null, tag + " Street", "Block A1",
                "Bucuresti", "Bucuresti", "Romania", "010101"));
        request.setItems(List.of(new CartItemDTO(productId, quantity)));
        return orderService.placeGuestOrder(request);
    }
}
