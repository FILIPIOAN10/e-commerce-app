package com.ecommerce.project.service.search;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.ProductRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The denormalised rating columns must equal what the reviews table says —
 * whatever route the reviews took to get there.
 *
 * <p>That last part is the point of re-deriving rather than incrementing: a
 * user's reviews can also disappear through a GDPR bulk delete that never passes
 * through {@code ReviewService} at all, and an incremented average would simply
 * be wrong from then on.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProductRatingAggregateTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    private final String tag = "ra" + Long.toUnsignedString(System.nanoTime(), 36);
    private final AtomicInteger seq = new AtomicInteger();
    private TransactionTemplate tx;
    private Long productId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> entityManager.createNativeQuery(
                        "SELECT setval(pg_get_serial_sequence('users', 'user_id'), "
                        + "GREATEST((SELECT COALESCE(MAX(user_id), 0) FROM users), 1))")
                .getResultList());

        productId = tx.execute(status -> {
            Category category = new Category();
            category.setCategoryName(tag + "-cat");
            entityManager.persist(category);

            Product product = new Product();
            product.setProductName(tag + "-widget");
            product.setDescription("rating aggregate fixture");
            product.setPrice(new BigDecimal("10.0"));
            product.setSpecialPrice(new BigDecimal("10.0"));
            product.setDiscount(new BigDecimal("0.0"));
            product.setQuantity(5);
            product.setCategory(category);
            entityManager.persist(product);
            return product.getProductId();
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM reviews WHERE product_id IN "
                            + "(SELECT product_id FROM products WHERE product_name LIKE :t)")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM products WHERE product_name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM categories WHERE category_name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE username LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
        });
    }

    @Test
    @DisplayName("the denormalised average tracks the reviews, and survives a bulk delete behind its back")
    void aggregateIsAlwaysReDerivedFromTheReviews() {
        assertThat(reload().getAverageRating()).as("no reviews yet").isZero();
        assertThat(reload().getReviewCount()).isZero();

        addReview(5);
        addReview(2);
        refreshAggregate();

        assertThat(reload().getAverageRating()).isCloseTo(3.5, within(0.001));
        assertThat(reload().getReviewCount()).isEqualTo(2);

        // Exactly what GDPR erasure does: delete the rows, then re-derive. An
        // incremented counter would have no way to know this happened.
        tx.executeWithoutResult(status -> entityManager
                .createQuery("DELETE FROM Review r WHERE r.product.productId = :id")
                .setParameter("id", productId)
                .executeUpdate());
        refreshAggregate();

        assertThat(reload().getAverageRating()).as("back to nothing").isZero();
        assertThat(reload().getReviewCount()).isZero();
    }

    /** The statement flushes the persistence context, so it needs a transaction. */
    private void refreshAggregate() {
        tx.executeWithoutResult(status -> productRepository.refreshRatingAggregate(productId));
    }

    private Product reload() {
        return tx.execute(status -> {
            entityManager.clear();
            return productRepository.findById(productId).orElseThrow();
        });
    }

    private void addReview(int rating) {
        tx.executeWithoutResult(status -> {
            int n = seq.incrementAndGet();
            User user = new User(tag + n, tag + n + "@e.co", "pw-hash");
            user.setVerified(true);
            entityManager.persist(user);

            Review review = Review.builder()
                    .user(user)
                    .product(entityManager.getReference(Product.class, productId))
                    .rating(rating)
                    .comment("rating " + rating)
                    .verifiedPurchase(true)
                    .helpfulCount(0)
                    .unhelpfulCount(0)
                    .build();
            entityManager.persist(review);
        });
    }
}
