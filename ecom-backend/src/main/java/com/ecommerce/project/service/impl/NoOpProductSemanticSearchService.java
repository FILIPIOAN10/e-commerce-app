package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.service.ProductSemanticSearchService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoOpProductSemanticSearchService implements ProductSemanticSearchService {
    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public List<Long> searchProductIds(String query, int limit) {
        return List.of();
    }

    @Override
    public void indexProduct(Product product) {

    }

    @Override
    public void deleteProduct(Long productId) {

    }
}
