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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money on an order is an exact decimal — in the column, in the round trip, and
 * in the sum.
 *
 * <p>The pricing pipeline has computed in {@link Money} for a while, but the
 * result was widened to {@code double} on the way into the order, so the
 * exactness ended at the last step and what the database held was a binary float
 * that cannot represent most cent amounts. These tests pin the three things that
 * changes.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderMoneyTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    private final String tag = "om" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;
    private Long productId;

    @BeforeEach
    void seedProduct() {
        tx = new TransactionTemplate(txManager);
        productId = tx.execute(status -> {
            Category category = new Category();
            category.setCategoryName(tag + "-cat");
            entityManager.persist(category);

            // 84.99 is not representable in binary floating point, and three of
            // them is the classic drift: 84.99 * 3 == 254.96999999999997.
            Product product = new Product();
            product.setProductName(tag + "-widget");
            product.setDescription("money fixture");
            product.setPrice(84.99);
            product.setSpecialPrice(84.99);
            product.setDiscount(0.0);
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
    @DisplayName("the order's money columns are decimal, not floating point")
    void moneyColumnsAreDecimal() {
        assertThat(columnType("orders", "total_amount")).isEqualTo("numeric");
        assertThat(columnType("orders", "discount_amount")).isEqualTo("numeric");
        assertThat(columnType("orders", "shipping_cost")).isEqualTo("numeric");
        assertThat(columnType("order_items", "ordered_product_price")).isEqualTo("numeric");
        assertThat(columnType("order_items", "discount")).isEqualTo("numeric");
    }

    @Test
    @DisplayName("a total survives the round trip to the cent, and equals its own lines")
    void totalRoundTripsExactly() {
        OrderDTO placed = placeGuestOrder(3);

        assertThat(placed.getTotalAmount())
                .as("84.99 x 3, to the cent")
                .isEqualByComparingTo("254.97");

        // Read it back through a fresh persistence context: this is the figure the
        // invoice, the GDPR export and the Stripe check all work from.
        Order reloaded = tx.execute(status -> {
            entityManager.clear();
            return orderRepository.findByIdWithDetails(placed.getOrderId()).orElseThrow();
        });

        assertThat(reloaded.getTotalAmount())
                .as("what the database gave back is what went in")
                .isEqualByComparingTo("254.97");
        assertThat(reloaded.getTotalAmount().scale())
                .as("stored to the cent, not to whatever a float happened to hold")
                .isEqualTo(2);

        BigDecimal lineSum = reloaded.getOrderItems().stream()
                .map(item -> item.getOrderedProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(reloaded.getTotalAmount())
                .as("the total is the sum of its lines, with nothing lost between them")
                .isEqualByComparingTo(lineSum);

        assertThat(Money.of(reloaded.getTotalAmount()).toCents())
                .as("and that is the amount the gateway is held to")
                .isEqualTo(25497L);
    }

    @Test
    @DisplayName("a hundred awkward amounts sum exactly, which is what a float could not do")
    void sumsAreExact() {
        // 0.07 has no exact binary representation. Added a hundred times as DOUBLE
        // PRECISION the running sum drifts off 7.00; as NUMERIC it cannot.
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < 100; i++) {
                Order order = new Order();
                order.setEmail(tag + "-sum-" + i + "@example.com");
                order.setOrderDate(LocalDate.now());
                order.setOrderStatus("Placed");
                order.setTotalAmount(new BigDecimal("0.07"));
                entityManager.persist(order);
            }
        });

        BigDecimal sum = tx.execute(status -> (BigDecimal) entityManager
                .createNativeQuery("SELECT SUM(total_amount) FROM orders WHERE email LIKE :t")
                .setParameter("t", tag + "%")
                .getSingleResult());

        assertThat(sum)
                .as("the revenue figures on the admin dashboard are built out of this sum")
                .isEqualByComparingTo("7.00");
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
