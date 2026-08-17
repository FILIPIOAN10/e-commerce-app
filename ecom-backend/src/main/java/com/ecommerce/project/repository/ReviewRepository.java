package com.ecommerce.project.repository;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProduct(Product product, Pageable pageable);

    Optional<Review> findByUserAndProduct(User user, Product product);

    boolean existsByUserAndProduct(User user, Product product);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.product = :product")
    Double getAverageRatingForProduct(@Param("product") Product product);

    long countByProduct(Product product);

    @Query("SELECT r.product.productId, COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.product.productId IN :productIds GROUP BY r.product.productId")
    List<Object[]> getAverageRatingsForProductIds(@Param("productIds") List<Long> productIds);

    @Query("SELECT r.product.productId, COUNT(r) FROM Review r WHERE r.product.productId IN :productIds GROUP BY r.product.productId")
    List<Object[]> getReviewCountsForProductIds(@Param("productIds") List<Long> productIds);

    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
            "FROM OrderItem oi JOIN oi.order o " +
            "WHERE o.email = :email AND oi.product = :product")
    boolean hasUserPurchasedProduct(@Param("email") String email, @Param("product") Product product);
}
