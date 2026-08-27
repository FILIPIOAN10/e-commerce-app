package com.ecommerce.project.controller;


import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.ApiResponse;
import com.ecommerce.project.payload.PaginationParams;
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

public class ProductController extends BaseController {

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
        return ok(productDTO);
    }

    @Tag(name = "Product")
    @GetMapping("/public/products")
    public ResponseEntity<ProductResponse> getAllProducts(
            @RequestParam(name = "keyword",required = false)  String keyword,
            @RequestParam(name = "category",required = false) String category,
            @ModelAttribute PaginationParams params
    ) {
        ProductResponse productResponse = productService.getAllProducts(params.getPageNumber(),params.getPageSize(),params.getSortBy(),params.getSortOrder(),keyword,category);
        return ok(productResponse);
    }





    /**
     * Returnează produsele care aparțin unei anumite categorii.
     */
    @Tag(name = "Product")
    @GetMapping("/public/categories/{categoryId}/products")
    public ResponseEntity<ProductResponse> getProductsByCategory(@PathVariable Long categoryId, @ModelAttribute PaginationParams params) {
        ProductResponse productResponse = productSearchService.searchByCategory(categoryId, params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder());
        return ok(productResponse);
    }
    /**
     * Caută produse pe baza unui keyword (în nume / descriere).
     */
    @Tag(name = "Product")
    @GetMapping("/public/products/keyword/{keyword}")
    public ResponseEntity<ProductResponse> getProductByKeyword(@PathVariable String keyword, @ModelAttribute PaginationParams params) {
        ProductResponse productResponse = productSearchService.searchProductByKeyword(keyword, params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder());
        return found(productResponse);
    }
    @Tag(name="Product")
    @GetMapping("/public/products/search")
    public ResponseEntity<ProductResponse> searchProducts(@RequestParam(name = "q") String query,
                                                          @RequestParam(name = "semantic",defaultValue = "true",required = false) Boolean semantic,
                                                          @ModelAttribute PaginationParams params){
        ProductResponse productResponse = productSearchService.searchProducts(query, params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder(), semantic);
        return ok(productResponse);
    }












    @Tag(name = "Product")
    @PostMapping("/user/products/{productId}/view")
    public ResponseEntity<ApiResponse> recordProductView(@PathVariable Long productId) {
        recentlyViewedService.recordProductView(productId);
        return ok(new ApiResponse("Product view recorded", true));
    }

    @Tag(name = "Product")
    @GetMapping("/user/products/recently-viewed")
    public ResponseEntity<List<ProductDTO>> getRecentlyViewedProducts() {
        List<ProductDTO> products = recentlyViewedService.getRecentlyViewedProducts();
        return ok(products);
    }

    @Tag(name = "Product")
    @GetMapping("/public/products/autocomplete")
    public ResponseEntity<List<String>> searchAutocomplete(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < 2) {
            return new ResponseEntity<>(List.of(), HttpStatus.OK);
        }
        List<String> suggestions = productSearchService.searchAutocomplete(query.trim());
        return ok(suggestions);
    }

    @Tag(name = "Product")
    @GetMapping("/public/products/featured")
    public ResponseEntity<List<ProductDTO>> getFeaturedProducts(
            @RequestParam(name = "type", defaultValue = "best-sellers") String type,
            @RequestParam(name = "limit", defaultValue = "8", required = false) int limit) {
        List<ProductDTO> products = switch (type.toLowerCase()) {
            case "new-arrivals" -> productSearchService.getNewArrivals(limit);
            case "on-sale" -> productSearchService.getOnSaleProducts(limit);
            default -> productSearchService.getBestSellers(limit);
        };
        return ok(products);
    }
}
