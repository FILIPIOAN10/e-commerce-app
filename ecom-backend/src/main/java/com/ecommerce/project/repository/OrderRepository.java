package com.ecommerce.project.repository;


import com.ecommerce.project.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order,Long> {
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    Double getTotalRevenue();
    Page<Order> findByEmail(String email, Pageable pageable);
    @Query("SELECT DISTINCT o FROM Order o JOIN o.orderItems oi " +
            "WHERE oi.product.user.userId = :sellerId")
    Page<Order> findOrdersBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query("SELECT FUNCTION('TO_CHAR', o.orderDate, 'YYYY-MM') as month, COALESCE(SUM(o.totalAmount), 0) as revenue " +
            "FROM Order o GROUP BY FUNCTION('TO_CHAR', o.orderDate, 'YYYY-MM') ORDER BY month")
    List<Object[]> getMonthlyRevenue();

    @Query("SELECT o.orderStatus, COUNT(o) FROM Order o GROUP BY o.orderStatus")
    List<Object[]> getOrderCountByStatus();
}
