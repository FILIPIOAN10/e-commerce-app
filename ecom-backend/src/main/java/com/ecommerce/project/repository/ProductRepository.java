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

import java.util.Collection;
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

    // Stock is not mutated from here. Every change to products.quantity goes
    // through StockLedgerService, which applies it and appends the movement that
    // explains it in one statement; a second door on the repository would be a
    // stock change with no ledger entry, which is exactly what the reconciliation
    // sweep exists to catch.

    /**
     * Re-derives the denormalised rating columns for the given products from the
     * reviews table.
     *
     * <p>Recomputed rather than adjusted: an incremented average survives one bug
     * and is wrong forever after, while this statement is correct whatever
     * happened before it — including a bulk delete of a user's reviews that never
     * went through {@code ReviewService} at all.
     *
     * <p>Deliberately does <em>not</em> bump {@code version}: these columns are
     * derived, never edited by a user, and an optimistic-lock conflict raised
     * because somebody left a review would be noise.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products p
            SET average_rating = COALESCE((SELECT AVG(r.rating) FROM reviews r WHERE r.product_id = p.product_id), 0),
                review_count   = (SELECT COUNT(*) FROM reviews r WHERE r.product_id = p.product_id)
            WHERE p.product_id IN (:productIds)
            """, nativeQuery = true)
    int refreshRatingAggregates(@Param("productIds") Collection<Long> productIds);

    default int refreshRatingAggregate(Long productId) {
        return refreshRatingAggregates(List.of(productId));
    }
}
