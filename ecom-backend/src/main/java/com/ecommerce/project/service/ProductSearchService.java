package com.ecommerce.project.service;

import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;

import java.util.List;

public interface ProductSearchService {

    ProductResponse searchByCategory(Long categoryId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    ProductResponse searchProductByKeyword(String keyword, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    ProductResponse searchProducts(String query, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, Boolean semantic);

    List<String> searchAutocomplete(String query);

    List<ProductDTO> getBestSellers(int limit);

    List<ProductDTO> getNewArrivals(int limit);

    List<ProductDTO> getOnSaleProducts(int limit);
}
