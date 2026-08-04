package com.ecommerce.project.repository;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


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
}
