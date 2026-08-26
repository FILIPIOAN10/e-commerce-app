package com.ecommerce.project.repository;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface ProductRepository extends JpaRepository<Product, Long> , JpaSpecificationExecutor<Product> {
    Page<Product> findByCategoryOrderByPriceAsc(Category category, Pageable pageDetails);

    Page<Product> findByProductNameLikeIgnoreCase(String keyword, Pageable pageDetails);

    Page<Product> findByUser(User user, Pageable pageDetails);
    boolean existsByCategoryAndProductName(Category category, String productName);

    @Query("SELECT p FROM Product p WHERE p.quantity <= COALESCE(p.lowStockThreshold, 10)")
    Page<Product> findLowStockProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.user = :user AND p.quantity <= COALESCE(p.lowStockThreshold, 10)")
    Page<Product> findLowStockProductsBySeller(@Param("user") User user, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.discount > 0 ORDER BY p.discount DESC")
    List<Product> findOnSaleProducts(Pageable pageable);

    List<Product> findAllByOrderByProductIdDesc(Pageable pageable);

    @Query("SELECT oi.product FROM OrderItem oi GROUP BY oi.product " +
            "ORDER BY SUM(oi.quantity) DESC")
    List<Product> findBestSellingProducts(Pageable pageable);

    /**
     * Atomically reduces stock only if enough is available.
     *
     * @return 1 when the stock was decremented, 0 when there was not enough stock.
     *         A return value of 0 means the caller must reject the operation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.quantity = p.quantity - :qty " +
           "WHERE p.productId = :id AND p.quantity >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Atomically returns stock to inventory, used when an order is cancelled or returned.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.quantity = p.quantity + :qty WHERE p.productId = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
