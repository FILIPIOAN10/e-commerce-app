package com.ecommerce.project.repository;

import com.ecommerce.project.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    void deleteByImageId(Long imageId);

    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.productId = :productId")
    List<ProductImage> findByProduct_ProductId(@Param("productId") Long productId);
}
