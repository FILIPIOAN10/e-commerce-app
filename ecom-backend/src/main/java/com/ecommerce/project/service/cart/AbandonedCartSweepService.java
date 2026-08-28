package com.ecommerce.project.service.cart;

import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.CartReminder;
import com.ecommerce.project.model.CartReminderStage;
import com.ecommerce.project.repository.CartReminderRepository;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.security.redis.CartRecoveryTokenService;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.CartReminderOutboxPayload;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Finds abandoned carts and enqueues one recovery email per {@code (cart, stage)}
 * through the transactional outbox.
 *
 * <p>The whole sweep runs in one transaction guarded by a Postgres
 * transaction-level advisory lock, so only one instance sweeps at a time and the
 * lock releases automatically on commit/rollback. Even without the lock the
 * {@code uk_cart_reminder_cart_stage} constraint makes a double-send impossible;
 * the lock just avoids two instances doing the same work.
 */
@Slf4j
@Service
public class AbandonedCartSweepService {

    /** Arbitrary constant key for pg_try_advisory_xact_lock — must be stable across instances. */
    private static final long ADVISORY_LOCK_KEY = 82_346_155L;

    private final CartRepository cartRepository;
    private final CartReminderRepository cartReminderRepository;
    private final CartRecoveryTokenService recoveryTokenService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final EntityManager entityManager;

    private final String frontendUrl;
    private final int pageSize;
    private final int maxRemindersPerSweep;
    private final int recentOrderCutoffDays;

    public AbandonedCartSweepService(CartRepository cartRepository,
                                     CartReminderRepository cartReminderRepository,
                                     CartRecoveryTokenService recoveryTokenService,
                                     OutboxEventPublisher outboxEventPublisher,
                                     EntityManager entityManager,
                                     @Value("${frontend.url:http://localhost:5173}") String frontendUrl,
                                     @Value("${app.abandoned-cart.page-size:100}") int pageSize,
                                     @Value("${app.abandoned-cart.max-reminders-per-sweep:500}") int maxRemindersPerSweep,
                                     @Value("${app.abandoned-cart.recent-order-cutoff-days:4}") int recentOrderCutoffDays) {
        this.cartRepository = cartRepository;
        this.cartReminderRepository = cartReminderRepository;
        this.recoveryTokenService = recoveryTokenService;
        this.outboxEventPublisher = outboxEventPublisher;
        this.entityManager = entityManager;
        this.frontendUrl = frontendUrl;
        this.pageSize = pageSize;
        this.maxRemindersPerSweep = maxRemindersPerSweep;
        this.recentOrderCutoffDays = recentOrderCutoffDays;
    }

    /** @return the number of reminders enqueued this sweep */
    @Transactional
    public int sweep() {
        if (!acquireAdvisoryLock()) {
            log.debug("Abandoned-cart sweep skipped — another instance holds the lock");
            return 0;
        }

        LocalDate recentOrderCutoff = LocalDate.now().minusDays(recentOrderCutoffDays);
        int enqueued = 0;

        for (CartReminderStage stage : CartReminderStage.values()) {
            Instant threshold = Instant.now().minus(stage.getInactivityThreshold());
            int pageNumber = 0;
            boolean lastPage = false;

            while (!lastPage && enqueued < maxRemindersPerSweep) {
                Page<Long> page = cartRepository.findAbandonedCartIds(
                        threshold, stage, recentOrderCutoff, PageRequest.of(pageNumber, pageSize));

                for (Long cartId : page.getContent()) {
                    if (enqueued >= maxRemindersPerSweep) {
                        break;
                    }
                    if (enqueueReminder(cartId, stage)) {
                        enqueued++;
                    }
                }

                lastPage = page.isLast();
                pageNumber++;
            }
        }
        return enqueued;
    }

    private boolean enqueueReminder(Long cartId, CartReminderStage stage) {
        Cart cart = cartRepository.findById(cartId).orElse(null);
        if (cart == null || cart.getUser() == null || cart.getCartItems().isEmpty()) {
            return false;
        }

        try {
            cartReminderRepository.saveAndFlush(new CartReminder(cart, stage));
        } catch (DataIntegrityViolationException alreadySent) {
            // Lost the race to (cart, stage) — someone already recorded this reminder.
            return false;
        }

        String token = recoveryTokenService.issue(cartId);
        String recoveryUrl = frontendUrl + "/cart/recover?token=" + token;

        List<CartItem> activeItems = cart.getCartItems().stream()
                .filter(i -> !Boolean.TRUE.equals(i.getSavedForLater()))
                .toList();

        outboxEventPublisher.publish(OutboxEventTypes.CART_ABANDONMENT_REMINDER,
                new CartReminderOutboxPayload(
                        cart.getUser().getEmail(),
                        cart.getUser().getUserName(),
                        cartId,
                        stage.name(),
                        activeItems.size(),
                        cart.getTotalPrice() != null ? cart.getTotalPrice() : 0.0,
                        recoveryUrl));
        return true;
    }

    private boolean acquireAdvisoryLock() {
        Object result = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
                .setParameter("key", ADVISORY_LOCK_KEY)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
