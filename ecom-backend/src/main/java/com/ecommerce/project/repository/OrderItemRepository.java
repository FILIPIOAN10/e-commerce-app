package com.ecommerce.project.repository;

import com.ecommerce.project.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem,Long> {

    @Query("SELECT oi.product.productName, SUM(oi.quantity) as totalSold " +
            "FROM OrderItem oi GROUP BY oi.product.productId, oi.product.productName " +
            "ORDER BY totalSold DESC LIMIT 10")
    List<Object[]> getTop10BestSellingProducts();
}
