package com.ecommerce.project.service.cart;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.CartReminder;
import com.ecommerce.project.model.CartReminderStage;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.CartReminderRepository;
import com.ecommerce.project.repository.OutboxEventRepository;
import com.ecommerce.project.security.redis.CartRecoveryTokenService;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §3 (High): abandoned-cart recovery. A sweep turns non-empty carts that have
 * been idle past a stage window — owned by an opted-in, verified user with no
 * recent order — into one outbox reminder per (cart, stage), and the
 * {@code (cart, stage)} unique constraint keeps it idempotent.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AbandonedCartSweepServiceTest {

    @Autowired private AbandonedCartSweepService sweepService;
    @Autowired private CartRecoveryService cartRecoveryService;
    @Autowired private CartRecoveryTokenService recoveryTokenService;
    @Autowired private CartReminderRepository cartReminderRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    // Short enough for User.userName (max 20) and email (max 50); unique per run.
    private final String tag = "abd" + Long.toUnsignedString(System.nanoTime(), 36);
    private final AtomicInteger seq = new AtomicInteger();

    @BeforeEach
    void alignIdentitySequences() {
        // ddl-auto=create-drop plus seeded rows leaves the identity sequences
        // behind the seeded ids; persisting a fresh row then collides on the PK.
        String[][] pk = {
                {"users", "user_id"}, {"carts", "cart_id"}, {"categories", "category_id"},
                {"products", "product_id"}, {"cart_items", "cart_item_id"}
        };
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            for (String[] tp : pk) {
                entityManager.createNativeQuery(
                        "SELECT setval(pg_get_serial_sequence('" + tp[0] + "', '" + tp[1] + "'), "
                        + "GREATEST((SELECT COALESCE(MAX(" + tp[1] + "), 0) FROM " + tp[0] + "), 1))")
                        .getResultList();
            }
        });
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "DELETE FROM cart_reminder WHERE cart_id IN "
                    + "(SELECT c.cart_id FROM carts c JOIN users u ON c.user_id = u.user_id WHERE u.username LIKE :t)")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM outbox_event WHERE event_type = 'CART_ABANDONMENT_REMINDER'").executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM cart_items WHERE cart_id IN "
                    + "(SELECT c.cart_id FROM carts c JOIN users u ON c.user_id = u.user_id WHERE u.username LIKE :t)")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM carts WHERE user_id IN (SELECT user_id FROM users WHERE username LIKE :t)")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM products WHERE product_name LIKE :t").setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM categories WHERE category_name LIKE :t").setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM users WHERE username LIKE :t").setParameter("t", tag + "%").executeUpdate();
        });
    }

    private long reminderOutboxEventsFor(Long cartId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> "CART_ABANDONMENT_REMINDER".equals(e.getEventType()))
                .filter(e -> e.getPayload().contains("\"cartId\":" + cartId))
                .count();
    }

    private Long createAbandonedCart(boolean optIn, boolean verified, Instant lastActivityAt) {
        return new TransactionTemplate(txManager).execute(status -> {
            int n = seq.incrementAndGet();
            User user = new User(tag + n, tag + n + "@e.co", "pw-hash");
            user.setMarketingOptIn(optIn);
            user.setVerified(verified);
            entityManager.persist(user);

            Category category = new Category();
            category.setCategoryName(tag + "-catg");
            entityManager.persist(category);

            Product product = new Product();
            product.setProductName(tag + "-widget");
            product.setDescription("widget for the sweep test");
            product.setQuantity(10);
            product.setPrice(new BigDecimal("20.0"));
            product.setSpecialPrice(new BigDecimal("20.0"));
            product.setDiscount(new BigDecimal("0.0"));
            product.setCategory(category);
            entityManager.persist(product);

            Cart cart = new Cart();
            cart.setUser(user);
            cart.setTotalPrice(new BigDecimal("40.0"));
            cart.setLastActivityAt(lastActivityAt); // @PrePersist only fills a null value
            entityManager.persist(cart);

            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(2);
            item.setDiscount(new BigDecimal("0.0"));
            item.setProductPrice(new BigDecimal("20.0"));
            item.setSavedForLater(false);
            entityManager.persist(item);

            return cart.getCartId();
        });
    }

    @Test
    @DisplayName("an opted-in idle cart gets exactly one FIRST-stage reminder, even across two sweeps")
    void optedInIdleCartGetsOneFirstStageReminder() {
        Long cartId = createAbandonedCart(true, true, Instant.now().minus(90, ChronoUnit.MINUTES));

        assertThat(sweepService.sweep()).isGreaterThanOrEqualTo(1);

        assertThat(cartReminderRepository.existsByCartCartIdAndStage(cartId, CartReminderStage.FIRST)).isTrue();
        assertThat(cartReminderRepository.existsByCartCartIdAndStage(cartId, CartReminderStage.SECOND)).isFalse();
        assertThat(reminderOutboxEventsFor(cartId)).isEqualTo(1);

        sweepService.sweep();
        assertThat(reminderOutboxEventsFor(cartId)).as("a second sweep does not re-send FIRST").isEqualTo(1);
    }

    @Test
    @DisplayName("a cart older than every window gets all three stages in one sweep")
    void veryOldCartGetsEveryStage() {
        Long cartId = createAbandonedCart(true, true, Instant.now().minus(100, ChronoUnit.HOURS));

        sweepService.sweep();

        assertThat(cartReminderRepository.existsByCartCartIdAndStage(cartId, CartReminderStage.FIRST)).isTrue();
        assertThat(cartReminderRepository.existsByCartCartIdAndStage(cartId, CartReminderStage.SECOND)).isTrue();
        assertThat(cartReminderRepository.existsByCartCartIdAndStage(cartId, CartReminderStage.FINAL)).isTrue();
        assertThat(reminderOutboxEventsFor(cartId)).isEqualTo(3);
    }

    @Test
    @DisplayName("a cart whose owner did not opt in is never reminded")
    void notOptedInIsSkipped() {
        Long cartId = createAbandonedCart(false, true, Instant.now().minus(100, ChronoUnit.HOURS));

        sweepService.sweep();

        assertThat(cartReminderRepository.findFirstByCartCartIdOrderBySentAtDesc(cartId)).isEmpty();
        assertThat(reminderOutboxEventsFor(cartId)).isZero();
    }

    @Test
    @DisplayName("a cart with recent activity is not yet abandoned")
    void recentlyActiveIsSkipped() {
        Long cartId = createAbandonedCart(true, true, Instant.now().minus(10, ChronoUnit.MINUTES));

        sweepService.sweep();

        assertThat(cartReminderRepository.findFirstByCartCartIdOrderBySentAtDesc(cartId)).isEmpty();
        assertThat(reminderOutboxEventsFor(cartId)).isZero();
    }

    @Test
    @DisplayName("following the recovery link stamps the reminder as recovered")
    void recoveryLinkMarksReminderRecovered() {
        Long cartId = createAbandonedCart(true, true, Instant.now().minus(90, ChronoUnit.MINUTES));
        sweepService.sweep();

        String token = recoveryTokenService.issue(cartId);
        assertThat(cartRecoveryService.recover(token)).contains(cartId);

        CartReminder reminder = cartReminderRepository.findFirstByCartCartIdOrderBySentAtDesc(cartId).orElseThrow();
        assertThat(reminder.getRecoveredAt()).isNotNull();
        assertThat(cartReminderRepository.countByStageAndRecoveredAtIsNotNull(CartReminderStage.FIRST))
                .isGreaterThanOrEqualTo(1);

        assertThat(cartRecoveryService.recover(token)).as("token is single-use").isEmpty();
    }
}
