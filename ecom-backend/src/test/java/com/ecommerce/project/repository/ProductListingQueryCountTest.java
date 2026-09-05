package com.ecommerce.project.repository;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page of products does not cost a query per row.
 *
 * <p>{@code Product.user} was an EAGER {@code @ManyToOne} and {@code User.roles}
 * is an EAGER {@code @ManyToMany}, so listing twenty products issued a select
 * per distinct seller and then a select for each of those sellers' roles —
 * roughly forty queries to render a list that needs one, each hydrating a whole
 * {@code User} including its password hash for a DTO with no seller field.
 *
 * <p>Asserted as a bound on statement count for a page of ten distinct sellers,
 * because the regression this guards against is precisely the one that scales
 * with rows: an assertion on behaviour would still pass while the page got
 * slower with every product added.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class ProductListingQueryCountTest {

    private static final int SELLERS = 10;

    @Autowired private ProductRepository productRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Category category;
    private final String tag = "qc" + Long.toUnsignedString(System.nanoTime(), 36);

    @BeforeEach
    void seed() {
        category = new Category();
        category.setCategoryName(tag + "-cat");
        entityManager.persist(category);

        // A distinct seller per product is the shape that used to hurt: shared
        // sellers would be deduplicated by the persistence context and hide it.
        for (int i = 0; i < SELLERS; i++) {
            User seller = new User(tag + i, tag + i + "@e.co", "pw-hash");
            entityManager.persist(seller);

            Product product = new Product();
            product.setProductName(tag + "-product-" + i);
            product.setDescription("query count fixture");
            product.setQuantity(5);
            product.setPrice(new BigDecimal("20.00"));
            product.setDiscount(new BigDecimal("0.00"));
            product.setSpecialPrice(new BigDecimal("20.00"));
            product.setCategory(category);
            product.setUser(seller);
            entityManager.persist(product);
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("listing a page of products does not query per seller")
    void listingDoesNotScaleWithSellers() {
        Statistics statistics = statistics();
        statistics.clear();

        Page<Product> page = productRepository.findByCategoryOrderByPriceAsc(
                category, PageRequest.of(0, SELLERS));

        assertThat(page.getContent()).hasSize(SELLERS);

        // One for the page, one for the count, and room for the category. Ten
        // sellers plus ten role lookups would put this past twenty.
        assertThat(statistics.getPrepareStatementCount())
                .as("a page of %s products should not cost a query per seller", SELLERS)
                .isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("the seller is still reachable when something actually needs it")
    void sellerRemainsReachable() {
        // Ownership checks read getUserId() off the proxy without a query, but
        // the association must still resolve when genuinely dereferenced.
        Product product = productRepository
                .findByCategoryOrderByPriceAsc(category, PageRequest.of(0, 1))
                .getContent()
                .getFirst();

        assertThat(product.getUser()).isNotNull();
        assertThat(product.getUser().getUserId()).isNotNull();
        assertThat(product.getUser().getUserName()).startsWith(tag);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
