package com.ecommerce.project.service;

import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;

public interface WishlistService {
    String addToWishlist(Long productId);

    String removeFromWishlist(Long productId);

    ProductResponse getWishlist(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
