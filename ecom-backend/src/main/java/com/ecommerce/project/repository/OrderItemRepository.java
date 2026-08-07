package com.ecommerce.project.repository;

import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Product;
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

    @Query("SELECT DISTINCT oi.product FROM OrderItem oi " +
            "WHERE oi.order.email = :email " +
            "ORDER BY oi.order.orderDate DESC")
    List<Product> findOrderedProductsByEmail(@Param("email") String email);
}
