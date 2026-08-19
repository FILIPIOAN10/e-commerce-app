package com.ecommerce.project.service;

import com.ecommerce.project.payload.ProductDTO;

import java.util.List;

public interface RecommendationService {

    List<ProductDTO> getRecommendedForUser(int limit);

    List<ProductDTO> getSimilarProducts(Long productId, int limit);

    List<ProductDTO> getFrequentlyBoughtTogether(Long productId, int limit);
}
