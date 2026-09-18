package com.ecommerce.project.repository;

import com.ecommerce.project.model.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BundleRepository extends JpaRepository<Bundle, Long> {
    List<Bundle> findByActiveTrue();

    // The list mappers walk bundle.products for every bundle; fetch that
    // many-to-many with the bundles. DISTINCT collapses the row multiplication
    // from the join. Unpaginated, so the fetch-join is safe.
    @Query("SELECT DISTINCT b FROM Bundle b LEFT JOIN FETCH b.products")
    List<Bundle> findAllWithProducts();

    @Query("SELECT DISTINCT b FROM Bundle b LEFT JOIN FETCH b.products WHERE b.active = true")
    List<Bundle> findByActiveTrueWithProducts();

    // findById() alone returned a Bundle whose products collection was still a
    // LAZY proxy: with open-in-view=false and no transaction around the caller,
    // BundleServiceImpl.mapToDTO walked bundle.getProducts() outside the
    // Hibernate session and threw LazyInitializationException. Fetch the graph
    // with the row, same shape as the list variants above.
    @Query("SELECT DISTINCT b FROM Bundle b LEFT JOIN FETCH b.products WHERE b.bundleId = :bundleId")
    Optional<Bundle> findByIdWithProducts(@Param("bundleId") Long bundleId);
}
