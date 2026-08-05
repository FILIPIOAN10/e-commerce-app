package com.ecommerce.project.service;

import com.ecommerce.project.payload.ProductDTO;

import java.util.List;

public interface RecentlyViewedService {
    void recordProductView(Long productId);

    List<ProductDTO> getRecentlyViewedProducts();
}
