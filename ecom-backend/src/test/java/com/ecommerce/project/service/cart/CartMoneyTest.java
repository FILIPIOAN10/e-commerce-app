package com.ecommerce.project.service.cart;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 3 of the money migration: the catalogue and the cart are exact decimals
 * too.
 *
 * <p>Orders were converted first, but every order total is a sum of cart lines
 * and every cart line is a product price — so an exact order built out of
 * floating-point inputs was only exact by luck. What is asserted here is the
 * property the columns now carry: a sum of many small prices is the number it
 * should be, not the number a binary float lands nearest to.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CartMoneyTest {

    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    private final String tag = "cm" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            tagged("DELETE FROM cart_items WHERE product_id IN "
                   + "(SELECT product_id FROM products WHERE product_name LIKE :t)");
            tagged("DELETE FROM stock_movement WHERE product_id IN "
                   + "(SELECT product_id FROM products WHERE product_name LIKE :t)");
            tagged("DELETE FROM products WHERE product_name LIKE :t");
            tagged("DELETE FROM categories WHERE category_name LIKE :t");
        });
    }

    @Test
    @DisplayName("the catalogue and cart money columns are decimal, not floating point")
    void moneyColumnsAreDecimal() {
        assertThat(columnType("products", "price")).isEqualTo("numeric");
        assertThat(columnType("products", "discount")).isEqualTo("numeric");
        assertThat(columnType("products", "special_price")).isEqualTo("numeric");
        assertThat(columnType("carts", "total_price")).isEqualTo("numeric");
        assertThat(columnType("cart_items", "discount")).isEqualTo("numeric");
        assertThat(columnType("cart_items", "product_price")).isEqualTo("numeric");
    }

    @Test
    @DisplayName("twenty lines at 0.07 sum to exactly 1.40, which a float column could not promise")
    void cartLinesSumExactly() {
        Long cartId = tx.execute(status -> {
            Category category = new Category();
            category.setCategoryName(tag + "-cat");
            entityManager.persist(category);

            // 0.07 has no exact binary representation; twenty of them added as
            // DOUBLE PRECISION drift off 1.40.
            Product product = new Product();
            product.setProductName(tag + "-penny-item");
            product.setDescription("cart money fixture");
            product.setPrice(new BigDecimal("0.07"));
            product.setSpecialPrice(new BigDecimal("0.07"));
            product.setDiscount(BigDecimal.ZERO);
            product.setQuantity(100);
            product.setCategory(category);
            entityManager.persist(product);

            Cart cart = new Cart();
            cart.setTotalPrice(new BigDecimal("1.40"));
            entityManager.persist(cart);

            for (int i = 0; i < 20; i++) {
                CartItem item = new CartItem();
                item.setCart(cart);
                item.setProduct(product);
                item.setQuantity(1);
                item.setProductPrice(new BigDecimal("0.07"));
                item.setDiscount(BigDecimal.ZERO);
                entityManager.persist(item);
            }
            entityManager.flush();
            return cart.getCartId();
        });

        // The sum the database itself computes over the lines.
        BigDecimal lineSum = tx.execute(status -> (BigDecimal) entityManager
                .createNativeQuery("SELECT SUM(product_price * quantity) FROM cart_items WHERE cart_id = :id")
                .setParameter("id", cartId)
                .getSingleResult());

        assertThat(lineSum)
                .as("exactly 1.40 — not 1.4000000000000001")
                .isEqualByComparingTo("1.40");

        BigDecimal storedTotal = tx.execute(status -> {
            entityManager.clear();
            return entityManager.find(Cart.class, cartId).getTotalPrice();
        });

        assertThat(storedTotal)
                .as("and the total the cart carries agrees with its own lines")
                .isEqualByComparingTo(lineSum);
        assertThat(storedTotal.scale()).as("held to the cent").isEqualTo(2);
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
}
