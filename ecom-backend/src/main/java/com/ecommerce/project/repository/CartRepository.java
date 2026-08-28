package com.ecommerce.project.repository;

import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartReminderStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CartRepository extends JpaRepository<Cart,Long> {


    //Filter this cart where this associated user's email matches the given parameter okay
    @Query("SELECT DISTINCT c FROM Cart c " +
           "JOIN FETCH c.user u " +
           "LEFT JOIN FETCH c.cartItems ci " +
           "LEFT JOIN FETCH ci.product p " +
           "WHERE u.email = ?1")
    Cart findCartByEmail(String email);


    @Query("SELECT c FROM Cart c WHERE c.user.email = ?1 AND c.cartId = ?2")
    Cart findCartByEmailAndCartId(String emailId, Long cartId);

    /**
     * Carts owned by a user. One-to-one in practice, but returned as a list so
     * the GDPR export and erasure do not assume it — a stray second row must be
     * exported and erased too, not silently skipped.
     */
    List<Cart> findByUserUserIdOrderByCartIdAsc(Long userId);

    @Query("SELECT c FROM Cart c JOIN  FETCH c.cartItems ci JOIN  FETCH  ci.product p WHERE  p.productId = ?1")
    List<Cart> findCartsByProductId(Long productId);

    /**
     * One page of cart ids that are candidates for an abandoned-cart reminder at
     * {@code stage}: inactive since before {@code threshold}, non-empty, owned by
     * an opted-in verified user, not already reminded at this stage, and with no
     * recent order (a coarse "don't nag a customer who just bought" guard —
     * checkout empties the cart, so a still-full cart was not converted).
     */
    @Query("""
            SELECT c.cartId FROM Cart c
            WHERE c.lastActivityAt <= :threshold
              AND size(c.cartItems) > 0
              AND c.user IS NOT NULL
              AND c.user.marketingOptIn = true
              AND c.user.verified = true
              AND NOT EXISTS (SELECT 1 FROM CartReminder r WHERE r.cart = c AND r.stage = :stage)
              AND NOT EXISTS (SELECT 1 FROM Order o
                              WHERE lower(o.email) = lower(c.user.email) AND o.orderDate >= :recentOrderCutoff)
            ORDER BY c.cartId
            """)
    Page<Long> findAbandonedCartIds(@Param("threshold") Instant threshold,
                                    @Param("stage") CartReminderStage stage,
                                    @Param("recentOrderCutoff") LocalDate recentOrderCutoff,
                                    Pageable pageable);
}
