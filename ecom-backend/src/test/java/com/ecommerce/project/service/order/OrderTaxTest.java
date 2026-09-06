package com.ecommerce.project.service.order;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.GuestCheckoutRequestDTO;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.pricing.Money;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VAT is a real line on the order — computed by the pricing pipeline, persisted
 * in its own NUMERIC(12,2) column, and included in the total the gateway is held
 * to.
 *
 * <p>Tax is disabled in the shared test profile (the other checkout tests assert
 * exact pre-tax totals), so this class turns it on explicitly and pins a 19%
 * Romanian rate.
 */
@SpringBootTest(properties = {
        "app.tax.enabled=true",
        "app.tax.default-rate-percent=0",
        "app.tax.taxable-shipping=true",
        "app.tax.rates.RO=19"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderTaxTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    private final String tag = "otx" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;
    private Long productId;

    @BeforeEach
    void seedProduct() {
        tx = new TransactionTemplate(txManager);
        productId = tx.execute(status -> {
            Category category = new Category();
            category.setCategoryName(tag + "-cat");
            entityManager.persist(category);

            Product product = new Product();
            product.setProductName(tag + "-widget");
            product.setDescription("tax fixture");
            product.setPrice(new BigDecimal("40.00"));
            product.setSpecialPrice(new BigDecimal("40.00"));
            product.setDiscount(new BigDecimal("0.0"));
            product.setQuantity(50);
            product.setCategory(category);
            entityManager.persist(product);
            return product.getProductId();
        });
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
                    .setParameter("s", tag + " Street")
                    .executeUpdate();
        });
    }

    @Test
    @DisplayName("the order has a tax_amount column, and it is decimal")
    void taxColumnIsDecimal() {
        assertThat(columnType("orders", "tax_amount")).isEqualTo("numeric");
    }

    @Test
    @DisplayName("VAT is computed on the discounted total plus shipping, persisted, and folded into the total")
    void vatIsChargedAndPersisted() {
        // 40 subtotal -> below the free-shipping threshold -> +3 domestic (Romania)
        // shipping = 43 -> *19% VAT = 8.17 -> total 51.17.
        OrderDTO placed = placeGuestOrder(1);

        assertThat(placed.getShippingCost()).isEqualByComparingTo("3.00");
        assertThat(placed.getTaxAmount()).isEqualByComparingTo("8.17");
        assertThat(placed.getTotalAmount()).isEqualByComparingTo("51.17");

        Order reloaded = tx.execute(status -> {
            entityManager.clear();
            return orderRepository.findByIdWithDetails(placed.getOrderId()).orElseThrow();
        });

        assertThat(reloaded.getTaxAmount())
                .as("what the database gave back is what went in")
                .isEqualByComparingTo("8.17");
        assertThat(reloaded.getTaxAmount().scale()).isEqualTo(2);

        assertThat(reloaded.getTotalAmount())
                .as("total = subtotal - discount + shipping + tax")
                .isEqualByComparingTo(
                        reloaded.getShippingCost()
                                .add(reloaded.getTaxAmount())
                                .add(new BigDecimal("40.00"))
                                .subtract(reloaded.getDiscountAmount()));

        assertThat(Money.of(reloaded.getTotalAmount()).toCents())
                .as("and that is the amount the gateway is held to")
                .isEqualTo(5117L);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String columnType(String table, String column) {
        return tx.execute(status -> (String) entityManager
                .createNativeQuery("SELECT data_type FROM information_schema.columns "
                                   + "WHERE table_name = :t AND column_name = :c")
                .setParameter("t", table)
                .setParameter("c", column)
                .getSingleResult());
    }

    private void tagged(String sql) {
        entityManager.createNativeQuery(sql)
                .setParameter("t", tag + "%")
                .executeUpdate();
    }

    private OrderDTO placeGuestOrder(int quantity) {
        GuestCheckoutRequestDTO request = new GuestCheckoutRequestDTO();
        request.setEmail(tag + "@example.com");
        request.setPaymentMethod("CASH");
        request.setPgPaymentId(null);
        request.setCouponCodes(List.of());
        request.setAddress(new AddressDTO(null, tag + " Street", "Block A1",
                "Bucuresti", "Bucuresti", "Romania", "010101"));
        request.setItems(List.of(new CartItemDTO(productId, quantity)));
        return orderService.placeGuestOrder(request);
    }
}
