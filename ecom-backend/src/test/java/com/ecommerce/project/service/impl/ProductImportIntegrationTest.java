package com.ecommerce.project.service.impl;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.service.ProductImportService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end guards for the two rewrites of the CSV import service:
 *
 * <ol>
 *   <li>the parser is RFC 4180 — a description that legitimately contains a
 *       comma inside quotes must import to the letter, not split into extra
 *       columns and either poison the row or reject it;</li>
 *   <li>the transaction is per-row — one bad row (unknown category, unparseable
 *       number) rejects only itself, not the whole file, and the successful
 *       rows are visible after the call.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("Product CSV import (opencsv + per-row transaction)")
class ProductImportIntegrationTest {

    private static final String CATEGORY = "CSVImportCategory";
    private static final String NAME_QUOTED = "CSVImportQuotedName";
    private static final String NAME_OK_1 = "CSVImportRowA";
    private static final String NAME_OK_2 = "CSVImportRowC";
    private static final String NAME_BAD = "CSVImportRowB";

    @Autowired private ProductImportService importService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    @BeforeEach
    void seedCategory() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (categoryRepository.findByCategoryName(CATEGORY) == null) {
                Category c = new Category();
                c.setCategoryName(CATEGORY);
                categoryRepository.save(c);
            }
        });
    }

    @AfterEach
    void cleanup() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "DELETE FROM products WHERE product_name IN (:names)")
                    .setParameter("names", java.util.List.of(NAME_QUOTED, NAME_OK_1, NAME_OK_2, NAME_BAD))
                    .executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM categories WHERE category_name = :n")
                    .setParameter("n", CATEGORY)
                    .executeUpdate();
        });
    }

    @Test
    @WithMockUser(username = "seller1", roles = "SELLER")
    @DisplayName("a description that contains a quoted comma imports as one field, not split")
    void quotedCommaInDescriptionSurvives() {
        // The old split(",") turned this into a 9-column row and either
        // rejected it as malformed or shifted every column right — the price
        // cell would have parsed "red shirt", 9.99 would have landed in
        // quantity, and the row would have failed for the wrong reason.
        String csv = "name,description,price,discount,quantity,image,tags,category\n"
                   + NAME_QUOTED + ",\"Nice, red shirt\",9.99,0,3,none.jpg,apparel," + CATEGORY + "\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        String summary = importService.importProducts(file);

        assertThat(summary).isEqualTo("Imported 1 products");
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            String description = (String) entityManager.createNativeQuery(
                    "SELECT description FROM products WHERE product_name = :n")
                    .setParameter("n", NAME_QUOTED)
                    .getSingleResult();
            assertThat(description).isEqualTo("Nice, red shirt");
        });
    }

    @Test
    @WithMockUser(username = "seller1", roles = "SELLER")
    @DisplayName("one bad row rejects only itself; the surrounding rows commit and are visible")
    void oneBadRowDoesNotRollBackTheOthers() {
        // Row B references a category that does not exist. Under the old
        // single-transaction implementation this threw APIException on row B
        // and rolled back rows A and C too — a 3-row file returned zero.
        String csv = "name,description,price,discount,quantity,image,tags,category\n"
                   + NAME_OK_1 + ",row one,10.00,0,1,none.jpg,tag," + CATEGORY + "\n"
                   + NAME_BAD  + ",row two,10.00,0,1,none.jpg,tag,NoSuchCategoryHere\n"
                   + NAME_OK_2 + ",row three,10.00,0,1,none.jpg,tag," + CATEGORY + "\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        String summary = importService.importProducts(file);

        assertThat(summary)
                .contains("Imported 2 products")
                .contains("1 row(s) failed")
                .contains("line 3")
                .contains("NoSuchCategoryHere");
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Number good = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM products WHERE product_name IN (:names)")
                    .setParameter("names", java.util.List.of(NAME_OK_1, NAME_OK_2))
                    .getSingleResult();
            assertThat(good.longValue()).as("the two good rows committed").isEqualTo(2);

            Number bad = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM products WHERE product_name = :n")
                    .setParameter("n", NAME_BAD)
                    .getSingleResult();
            assertThat(bad.longValue()).as("the bad row did not commit").isZero();
        });
    }
}
