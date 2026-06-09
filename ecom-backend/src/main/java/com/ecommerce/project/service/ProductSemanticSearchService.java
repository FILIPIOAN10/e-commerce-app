package com.ecommerce.project.service;

import com.ecommerce.project.model.Product;

import java.util.List;

public interface ProductSemanticSearchService {

    boolean isEnabled();

    List<Long> searchProductIds(String query, int limit);
    void indexProduct(Product product);
    void deleteProduct(Long productId);
}
