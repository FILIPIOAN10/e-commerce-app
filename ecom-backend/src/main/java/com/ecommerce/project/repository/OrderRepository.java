package com.ecommerce.project.repository;


import com.ecommerce.project.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order,Long> {
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    BigDecimal getTotalRevenue();

    @EntityGraph(value = "Order.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query(value = "SELECT DISTINCT o FROM Order o",
            countQuery = "SELECT COUNT(o) FROM Order o")
    Page<Order> findAllWithDetails(Pageable pageable);

    @EntityGraph(value = "Order.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query(value = "SELECT DISTINCT o FROM Order o WHERE o.email = :email",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.email = :email")
    Page<Order> findByEmailWithDetails(@Param("email") String email, Pageable pageable);

    @EntityGraph(value = "Order.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query(value = "SELECT DISTINCT o FROM Order o WHERE o.id IN " +
                   "(SELECT o2.id FROM Order o2 JOIN o2.orderItems oi2 JOIN oi2.product p2 WHERE p2.user.userId = :sellerId)",
            countQuery = "SELECT COUNT(DISTINCT o) FROM Order o JOIN o.orderItems oi JOIN oi.product p WHERE p.user.userId = :sellerId")
    Page<Order> findOrdersBySellerIdWithDetails(@Param("sellerId") Long sellerId, Pageable pageable);

    @EntityGraph(value = "Order.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    // ─────────────────────────────────────────────────────────────────────────
    //  Two-phase pagination.
    //
    //  Combining a collection fetch (orderItems, via the "Order.withDetails"
    //  entity graph) with a Pageable forces Hibernate to paginate IN MEMORY:
    //  it loads every matching row before applying the offset/limit. On a large
    //  orders table that is an OutOfMemoryError waiting to happen.
    //
    //  Phase 1 pages over IDs only (no collection => real SQL LIMIT/OFFSET).
    //  Phase 2 fetches the full graph for just that page of IDs.
    // ─────────────────────────────────────────────────────────────────────────

    @Query(value = "SELECT o.id FROM Order o",
            countQuery = "SELECT COUNT(o) FROM Order o")
    Page<Long> findAllIds(Pageable pageable);

    @Query(value = "SELECT o.id FROM Order o WHERE o.email = :email",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.email = :email")
    Page<Long> findIdsByEmail(@Param("email") String email, Pageable pageable);

    @Query(value = "SELECT DISTINCT o.id FROM Order o JOIN o.orderItems oi JOIN oi.product p " +
                   "WHERE p.user.userId = :sellerId",
            countQuery = "SELECT COUNT(DISTINCT o.id) FROM Order o JOIN o.orderItems oi JOIN oi.product p " +
                         "WHERE p.user.userId = :sellerId")
    Page<Long> findIdsBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    @EntityGraph(value = "Order.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT DISTINCT o FROM Order o WHERE o.id IN :ids")
    List<Order> findByIdInWithDetails(@Param("ids") List<Long> ids);

    @Query("SELECT FUNCTION('TO_CHAR', o.orderDate, 'YYYY-MM') as month, COALESCE(SUM(o.totalAmount), 0) as revenue " +
            "FROM Order o GROUP BY FUNCTION('TO_CHAR', o.orderDate, 'YYYY-MM') ORDER BY month")
    List<Object[]> getMonthlyRevenue();

    @Query("SELECT o.orderStatus, COUNT(o) FROM Order o GROUP BY o.orderStatus")
    List<Object[]> getOrderCountByStatus();

    /** {@code [orderId, totalAmount]} rows — for resolving many order totals in one query. */
    @Query("SELECT o.id, o.totalAmount FROM Order o WHERE o.id IN :ids")
    List<Object[]> findTotalsByIds(@Param("ids") java.util.Collection<Long> ids);

    /**
     * Every order placed with this address, fully loaded. Unpaged on purpose:
     * the GDPR export owes the customer their whole order history in one
     * archive, and one person's orders are a bounded set.
     */
    @EntityGraph(value = "Order.withDetails", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT DISTINCT o FROM Order o WHERE lower(o.email) = lower(:email) ORDER BY o.id")
    List<Order> findAllByEmailIgnoreCaseWithDetails(@Param("email") String email);
}
