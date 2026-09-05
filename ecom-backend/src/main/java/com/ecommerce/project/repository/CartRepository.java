package com.ecommerce.project.repository;

import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartReminderStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * Claims the one cart a user is allowed (see the unique index added in V26),
     * doing nothing if they already have one.
     *
     * <p>Native because the guarantee lives in {@code ON CONFLICT}: the check
     * and the insert are one statement, so a concurrent first-touch cannot slip
     * between them. It is also why this is not {@code save()} — a duplicate-key
     * exception inside the caller's transaction would mark it rollback-only,
     * and no amount of catching brings that back.
     */
    @Modifying
    @Query(value = "INSERT INTO carts (user_id, total_price, last_activity_at) "
                 + "VALUES (:userId, 0.00, CURRENT_TIMESTAMP) "
                 + "ON CONFLICT (user_id) DO NOTHING",
           nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId);

    /**
     * Carts owned by a user. One-to-one in practice, but returned as a list so
     * the GDPR export and erasure do not assume it — a stray second row must be
     * exported and erased too, not silently skipped.
     */
    List<Cart> findByUserUserIdOrderByCartIdAsc(Long userId);

    @Query("SELECT c FROM Cart c JOIN  FETCH c.cartItems ci JOIN  FETCH  ci.product p WHERE  p.productId = ?1")
    List<Cart> findCartsByProductId(Long productId);

    /**
     * Page 1 of the admin cart list: ids only. A JOIN FETCH plus a Pageable
     * paginates in memory, so the graph is fetched separately below for exactly
     * the ids on this page.
     */
    @Query("SELECT c.cartId FROM Cart c")
    Page<Long> findAllIds(Pageable pageable);

    /** Phase 2: the full graph for one page, in one query instead of N+M. */
    @Query("SELECT DISTINCT c FROM Cart c "
         + "LEFT JOIN FETCH c.cartItems ci LEFT JOIN FETCH ci.product "
         + "WHERE c.cartId IN :ids")
    List<Cart> findAllByIdsWithItems(@Param("ids") List<Long> ids);

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
