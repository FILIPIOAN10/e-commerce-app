package com.ecommerce.project.service.cart;

import com.ecommerce.project.model.CartReminder;
import com.ecommerce.project.repository.CartReminderRepository;
import com.ecommerce.project.security.redis.CartRecoveryTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Resolves an abandoned-cart recovery link: validates the signed token and
 * records the recovery against the reminder that produced it, so the feature
 * carries a conversion number (sent vs. recovered — see
 * {@link CartReminderRepository#countByStageAndRecoveredAtIsNotNull}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartRecoveryService {

    private final CartRecoveryTokenService recoveryTokenService;
    private final CartReminderRepository cartReminderRepository;

    /**
     * @return the recovered cart id if the token was valid, empty otherwise
     */
    @Transactional
    public Optional<Long> recover(String token) {
        Optional<Long> cartId = recoveryTokenService.consume(token);
        if (cartId.isEmpty()) {
            return Optional.empty();
        }

        cartReminderRepository.findFirstByCartCartIdOrderBySentAtDesc(cartId.get())
                .filter(reminder -> reminder.getRecoveredAt() == null)
                .ifPresent(reminder -> {
                    reminder.setRecoveredAt(Instant.now());
                    log.info("Cart {} recovered from a {} reminder", cartId.get(), reminder.getStage());
                });

        return cartId;
    }
}
