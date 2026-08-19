package com.ecommerce.project.repository;

import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem,Long> {

    @Query("SELECT oi.product.productName, SUM(oi.quantity) as totalSold " +
            "FROM OrderItem oi GROUP BY oi.product.productId, oi.product.productName " +
            "ORDER BY totalSold DESC LIMIT 10")
    List<Object[]> getTop10BestSellingProducts();

    @Query("SELECT oi.product FROM OrderItem oi " +
            "WHERE oi.order.email = :email " +
            "GROUP BY oi.product " +
            "ORDER BY MAX(oi.order.orderDate) DESC")
    List<Product> findOrderedProductsByEmail(@Param("email") String email);

    @Query("SELECT COALESCE(c.categoryName, 'Uncategorized'), SUM(oi.orderedProductPrice * oi.quantity) " +
            "FROM OrderItem oi JOIN oi.product p LEFT JOIN p.category c " +
            "GROUP BY COALESCE(c.categoryName, 'Uncategorized') " +
            "ORDER BY 2 DESC")
    List<Object[]> getRevenueByCategory();

    @Query("SELECT oi2.product.productId AS productId, COUNT(DISTINCT oi1.order.id) AS orderCount, " +
            "SUM(oi2.quantity) AS totalQuantity " +
            "FROM OrderItem oi1, OrderItem oi2 " +
            "WHERE oi1.order.id = oi2.order.id " +
            "  AND oi1.product.productId = :productId " +
            "  AND oi2.product.productId <> :productId " +
            "GROUP BY oi2.product.productId " +
            "ORDER BY orderCount DESC, totalQuantity DESC")
    List<Object[]> findFrequentlyBoughtTogether(@Param("productId") Long productId, Pageable pageable);
}
