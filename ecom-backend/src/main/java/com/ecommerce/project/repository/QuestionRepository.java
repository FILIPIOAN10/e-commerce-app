package com.ecommerce.project.repository;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionRepository extends JpaRepository<ProductQuestion, Long> {

    // The DTO mapper reads the asker's name for every row.
    @EntityGraph(attributePaths = {"user", "product"})
    Page<ProductQuestion> findByProduct(Product product, Pageable pageable);

    long countByProduct(Product product);

    /** Every question this user has asked — read by the GDPR export. */
    @EntityGraph(attributePaths = {"product"})
    java.util.List<ProductQuestion> findByUserOrderByIdAsc(com.ecommerce.project.model.User user);
}
