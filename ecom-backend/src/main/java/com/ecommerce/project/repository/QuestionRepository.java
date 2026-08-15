package com.ecommerce.project.repository;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionRepository extends JpaRepository<ProductQuestion, Long> {

    Page<ProductQuestion> findByProduct(Product product, Pageable pageable);

    long countByProduct(Product product);
}
