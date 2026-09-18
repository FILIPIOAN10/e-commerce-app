package com.ecommerce.project.service.impl;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.service.ProductImportService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the CSV importer against re-parsing money through {@code double}.
 *
 * <p>The old code did {@code BigDecimal.valueOf(Double.parseDouble(cell))}, which
 * routed the amount through a binary float on the way in. {@code 19.99} in IEEE 754
 * is {@code 19.989999999999998}, and once {@link com.ecommerce.project.service.pricing.Money}
 * multiplied that by a discount percent the special price was off by a fraction of
 * a cent — the exact regression the money migration (V24/V25/V30) was meant to end.
 * The fix parses each numeric cell directly into {@link BigDecimal}; this test
 * pins that in as a Postgres round-trip.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("Product CSV import money round-trip")
class ProductImportPriceRoundTripTest {

    @Autowired private ProductImportService importService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    private static final String CATEGORY = "CSVImportRoundTripCategory";
    private static final String PRODUCT = "CSVImportRoundTripProduct";

    @Test
    @WithMockUser(username = "seller1", roles = "SELLER")
    @DisplayName("19.99 with a 10% discount round-trips as 19.99 / 17.991, not 19.989999… / 17.9910000…")
    void priceAndSpecialPriceRoundTripExactly() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            if (categoryRepository.findByCategoryName(CATEGORY) == null) {
                Category c = new Category();
                c.setCategoryName(CATEGORY);
                categoryRepository.save(c);
            }
        });

        String csv = "name,description,price,discount,quantity,image,tags,category\n"
                   + PRODUCT + ",a headphone that must round-trip,19.99,10,5,none.jpg,audio," + CATEGORY + "\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        try {
            importService.importProducts(file);

            tx.executeWithoutResult(status -> {
                entityManager.clear();
                Object[] row = (Object[]) entityManager.createNativeQuery(
                        "SELECT price, discount, special_price FROM products WHERE product_name = :n")
                        .setParameter("n", PRODUCT)
                        .getSingleResult();
                BigDecimal price = (BigDecimal) row[0];
                BigDecimal discount = (BigDecimal) row[1];
                BigDecimal specialPrice = (BigDecimal) row[2];

                assertThat(price)
                        .as("price stored as an exact decimal, not the double approximation 19.989999999999998")
                        .isEqualByComparingTo("19.99");
                assertThat(discount).isEqualByComparingTo("10");
                assertThat(specialPrice)
                        .as("special price is price × (1 - discount%) = 19.99 × 0.90, to the cent")
                        .isEqualByComparingTo("17.99");
            });
        } finally {
            tx.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                        "DELETE FROM products WHERE product_name = :n")
                        .setParameter("n", PRODUCT)
                        .executeUpdate();
                entityManager.createNativeQuery(
                        "DELETE FROM categories WHERE category_name = :n")
                        .setParameter("n", CATEGORY)
                        .executeUpdate();
            });
        }
    }
}
