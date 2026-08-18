package com.ecommerce.project.controller;


import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.ApiResponse;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.service.ProductSearchService;
import com.ecommerce.project.service.ProductService;
import com.ecommerce.project.service.RecentlyViewedService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")

// Controller pentru operații CRUD pe produse (public și admin)

public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final RecentlyViewedService recentlyViewedService;

    public ProductController(ProductService productService, ProductSearchService productSearchService, RecentlyViewedService recentlyViewedService) {
        this.productService = productService;
        this.productSearchService = productSearchService;
        this.recentlyViewedService = recentlyViewedService;
    }
    /**
     * Returnează toate produsele cu paginare și sortare.
     */
    @Tag(name = "Product")
    @GetMapping("/public/products/{productId}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long productId) {
        ProductDTO productDTO = productService.getProductById(productId);
        return new ResponseEntity<>(productDTO, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @GetMapping("/public/products")
    public ResponseEntity<ProductResponse> getAllProducts(
            @RequestParam(name = "keyword",required = false)  String keyword,
            @RequestParam(name = "category",required = false) String category,
            @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
            @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false)  String sortBy,
            @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder

    ) {
        ProductResponse productResponse = productService.getAllProducts(pageNumber,pageSize,sortBy,sortOrder,keyword,category);
        return new ResponseEntity<>(productResponse, HttpStatus.OK);
    }





    /**
     * Returnează produsele care aparțin unei anumite categorii.
     */
    @Tag(name = "Product")
    @GetMapping("/public/categories/{categoryId}/products")
    public ResponseEntity<ProductResponse> getProductsByCategory(@PathVariable Long categoryId,
                                                                 @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
                                                                 @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
                                                                 @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false)  String sortBy,
                                                                 @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder) {
        ProductResponse productResponse = productSearchService.searchByCategory(categoryId, pageNumber, pageSize, sortBy, sortOrder);

        return new ResponseEntity<>(productResponse, HttpStatus.OK);
    }
    /**
     * Caută produse pe baza unui keyword (în nume / descriere).
     */
    @Tag(name = "Product")
    @GetMapping("/public/products/keyword/{keyword}")
    public ResponseEntity<ProductResponse> getProductByKeyword(@PathVariable String keyword,
                                                               @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
                                                               @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
                                                               @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false)  String sortBy,
                                                               @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder
                                                               ) {
        ProductResponse productResponse = productSearchService.searchProductByKeyword(keyword, pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(productResponse, HttpStatus.FOUND);

    }
    @Tag(name="Product")
    @GetMapping("/public/products/search")
    public ResponseEntity<ProductResponse> searchProducts(@RequestParam(name = "q") String query,
                                                          @RequestParam(name = "semantic",defaultValue = "true",required = false) Boolean semantic,
                                                          @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
                                                          @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
                                                          @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false) String sortBy,
                                                          @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder){
        ProductResponse productResponse = productSearchService.searchProducts(query, pageNumber, pageSize, sortBy, sortOrder, semantic);
        return new ResponseEntity<>(productResponse,HttpStatus.OK);
    }












    @Tag(name = "Product")
    @PostMapping("/user/products/{productId}/view")
    public ResponseEntity<ApiResponse> recordProductView(@PathVariable Long productId) {
        recentlyViewedService.recordProductView(productId);
        return new ResponseEntity<>(new ApiResponse("Product view recorded", true), HttpStatus.OK);
    }

    @Tag(name = "Product")
    @GetMapping("/user/products/recently-viewed")
    public ResponseEntity<List<ProductDTO>> getRecentlyViewedProducts() {
        List<ProductDTO> products = recentlyViewedService.getRecentlyViewedProducts();
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @GetMapping("/public/products/autocomplete")
    public ResponseEntity<List<String>> searchAutocomplete(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < 2) {
            return new ResponseEntity<>(List.of(), HttpStatus.OK);
        }
        List<String> suggestions = productSearchService.searchAutocomplete(query.trim());
        return new ResponseEntity<>(suggestions, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @GetMapping("/public/products/best-sellers")
    public ResponseEntity<List<ProductDTO>> getBestSellers(
            @RequestParam(name = "limit", defaultValue = "8", required = false) int limit) {
        List<ProductDTO> products = productSearchService.getBestSellers(limit);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @GetMapping("/public/products/new-arrivals")
    public ResponseEntity<List<ProductDTO>> getNewArrivals(
            @RequestParam(name = "limit", defaultValue = "8", required = false) int limit) {
        List<ProductDTO> products = productSearchService.getNewArrivals(limit);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @GetMapping("/public/products/on-sale")
    public ResponseEntity<List<ProductDTO>> getOnSaleProducts(
            @RequestParam(name = "limit", defaultValue = "8", required = false) int limit) {
        List<ProductDTO> products = productSearchService.getOnSaleProducts(limit);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }
}
