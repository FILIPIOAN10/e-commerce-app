package com.ecommerce.project.repository;

import com.ecommerce.project.model.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
