package com.ecommerce.project.repository;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Bundle;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductQuestion;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.model.User;
import com.ecommerce.project.model.Wishlist;
import com.ecommerce.project.payload.BundleDTO;
import com.ecommerce.project.payload.ReturnRequestDTO;
import com.ecommerce.project.service.BundleService;
import com.ecommerce.project.service.ReturnService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Hibernate;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F9: list endpoints beyond cart/order used to load an association per row inside
 * the DTO-mapping loop. Each path here is asserted to run in a bounded number of
 * queries that does not grow with the row count (N = 10).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class RepositoryNPlusOneAuditTest {

    private static final int N = 10;

    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private WishlistRepository wishlistRepository;
    @Autowired private BundleService bundleService;
    @Autowired private ReturnService returnService;

    @BeforeEach
    void alignIdentitySequences() {
        // create-drop + seeded rows leaves identity sequences behind the seeded
        // ids; a fresh persist would then collide on the PK. (setval is not
        // transactional, so this survives the test rollback — harmless.)
        String[][] pk = {{"users", "user_id"}, {"products", "product_id"}, {"categories", "category_id"},
                {"orders", "id"}, {"bundles", "bundle_id"}};
        for (String[] tp : pk) {
            em.createNativeQuery("SELECT setval(pg_get_serial_sequence('" + tp[0] + "', '" + tp[1] + "'), "
                    + "GREATEST((SELECT COALESCE(MAX(" + tp[1] + "), 0) FROM " + tp[0] + "), 1))").getResultList();
        }
    }

    private Statistics stats() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    private long measure(Runnable work) {
        em.flush();
        em.clear();
        Statistics s = stats();
        s.clear();
        work.run();
        return s.getQueryExecutionCount();
    }

    private User newUser(int i) {
        User u = new User("npu" + i + "x" + (System.nanoTime() % 100000), "npu" + i + "x" + (System.nanoTime() % 100000) + "@e.co", "pw");
        u.setVerified(true);
        em.persist(u);
        return u;
    }

    private Category newCategory() {
        Category c = new Category();
        c.setCategoryName("np-audit-category");
        em.persist(c);
        return c;
    }

    private Product newProduct(Category category, int i) {
        Product p = new Product();
        p.setProductName("np-audit-product-" + i);
        p.setDescription("np audit product description");
        p.setQuantity(50);
        p.setPrice(new BigDecimal("30.0"));
        p.setSpecialPrice(new BigDecimal("25.0"));
        p.setDiscount(new BigDecimal("0.0"));
        p.setCategory(category);
        em.persist(p);
        return p;
    }

    @Test
    @DisplayName("findByProduct on reviews loads reviewer + product with the page, not per row")
    void reviewListAvoidsNPlusOne() {
        Category category = newCategory();
        Product product = newProduct(category, 0);
        for (int i = 0; i < N; i++) {
            Review r = new Review();
            r.setUser(newUser(i));
            r.setProduct(product);
            r.setRating(4);
            r.setComment("solid");
            r.setVerifiedPurchase(true);
            r.setHelpfulCount(0);
            r.setUnhelpfulCount(0);
            r.setCreatedAt(LocalDateTime.now());
            em.persist(r);
        }
        long queries = measure(() -> {
            Page<Review> page = reviewRepository.findByProduct(product, PageRequest.of(0, N));
            assertThat(page.getContent()).hasSize(N);
            page.getContent().forEach(r -> {
                assertThat(Hibernate.isInitialized(r.getUser())).isTrue();
                r.getUser().getUserName();
                r.getProduct().getProductName();
            });
        });

        assertThat(queries).as("one page query (+ its count), not one per review").isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("findByProduct on questions loads the asker with the page, not per row")
    void questionListAvoidsNPlusOne() {
        Category category = newCategory();
        Product product = newProduct(category, 0);
        for (int i = 0; i < N; i++) {
            ProductQuestion q = new ProductQuestion();
            q.setProduct(product);
            q.setUser(newUser(i));
            q.setQuestion("does it ship?");
            q.setCreatedAt(LocalDateTime.now());
            em.persist(q);
        }
        long queries = measure(() -> {
            Page<ProductQuestion> page = questionRepository.findByProduct(product, PageRequest.of(0, N));
            assertThat(page.getContent()).hasSize(N);
            page.getContent().forEach(q -> {
                assertThat(Hibernate.isInitialized(q.getUser())).isTrue();
                q.getUser().getUserName();
            });
        });

        assertThat(queries).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("findByUser on wishlist loads the product with the page; images batch")
    void wishlistListAvoidsNPlusOne() {
        Category category = newCategory();
        User owner = newUser(999);
        for (int i = 0; i < N; i++) {
            Wishlist w = new Wishlist();
            w.setUser(owner);
            w.setProduct(newProduct(category, i));
            w.setCreatedAt(LocalDateTime.now());
            em.persist(w);
        }
        long queries = measure(() -> {
            Page<Wishlist> page = wishlistRepository.findByUser(owner, PageRequest.of(0, N));
            assertThat(page.getContent()).hasSize(N);
            page.getContent().forEach(w -> {
                assertThat(Hibernate.isInitialized(w.getProduct())).isTrue();
                w.getProduct().getProductImages().size(); // batched, not one query per product
            });
        });

        // page (+count) + the two review-aggregate queries + at most one batched image load.
        assertThat(queries).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("getActiveBundles maps every bundle's products without a query per bundle")
    void activeBundlesAvoidNPlusOne() {
        Category category = newCategory();
        List<Product> products = new java.util.ArrayList<>();
        for (int i = 0; i < N; i++) {
            products.add(newProduct(category, i));
        }
        for (int b = 0; b < 4; b++) {
            Bundle bundle = new Bundle();
            bundle.setName("np-audit-bundle-" + b);
            bundle.setActive(true);
            bundle.setDiscountPercentage(10.0);
            bundle.setProducts(products.subList(0, 3 + b));
            em.persist(bundle);
        }

        long queries = measure(() -> {
            List<BundleDTO> dtos = bundleService.getActiveBundles();
            assertThat(dtos).hasSize(4);
            assertThat(dtos).allSatisfy(d -> assertThat(d.getProducts()).isNotEmpty());
        });

        // one bundles+products fetch, two review-aggregate queries, one batched image load.
        assertThat(queries).as("constant, not one query per bundle").isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("getAllReturnRequests resolves every row's order total in one query")
    void returnRequestListAvoidsNPlusOne() {
        for (int i = 0; i < N; i++) {
            Order order = new Order();
            order.setEmail("npret" + i + "@e.co");
            order.setOrderDate(LocalDate.now());
            order.setOrderStatus("Delivered");
            order.setTotalAmount(BigDecimal.valueOf(100.0 + i));
            em.persist(order);

            ReturnRequest rr = new ReturnRequest();
            rr.setOrderId(order.getId());
            rr.setUserEmail(order.getEmail());
            rr.setReason("changed my mind");
            rr.setStatus("REQUESTED");
            rr.setRequestedAt(LocalDateTime.now().minusMinutes(i));
            em.persist(rr);
        }

        long queries = measure(() -> {
            Page<ReturnRequestDTO> page = returnService.getAllReturnRequests(0, N + 5);
            assertThat(page.getContent()).hasSizeGreaterThanOrEqualTo(N);
        });

        // list query (+count) + one batched totals query.
        assertThat(queries).isLessThanOrEqualTo(3);
    }
}
