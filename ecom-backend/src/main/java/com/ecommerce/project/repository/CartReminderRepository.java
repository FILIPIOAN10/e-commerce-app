package com.ecommerce.project.repository;

import com.ecommerce.project.model.CartReminder;
import com.ecommerce.project.model.CartReminderStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartReminderRepository extends JpaRepository<CartReminder, Long> {

    boolean existsByCartCartIdAndStage(Long cartId, CartReminderStage stage);

    /** The most recent reminder for a cart — the one a recovery-link click resolves. */
    Optional<CartReminder> findFirstByCartCartIdOrderBySentAtDesc(Long cartId);

    long countByStage(CartReminderStage stage);

    long countByStageAndRecoveredAtIsNotNull(CartReminderStage stage);
}
