package com.ecommerce.project.repository;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.model.Wishlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    Optional<Wishlist> findByUserAndProduct(User user, Product product);

    // Every row is rendered as its product; fetch the (to-one) product with the
    // page so it is not one query per wishlist entry. Product.productImages is
    // still lazy — batched via @BatchSize on that collection.
    @EntityGraph(attributePaths = {"product"})
    Page<Wishlist> findByUser(User user, Pageable pageable);
    long countByUser(User user);
    void deleteByUserAndProduct(User user, Product product);
    boolean existsByUserAndProduct(User user, Product product);
}
