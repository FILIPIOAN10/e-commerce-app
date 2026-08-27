package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.service.RecommendationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Tag(name = "Recommendations")
    @GetMapping("/user/recommendations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductDTO>> getRecommendedForUser(
            @RequestParam(name = "limit", defaultValue = "8", required = false) int limit) {
        List<ProductDTO> recommendations = recommendationService.getRecommendedForUser(limit);
        return ResponseEntity.ok(recommendations);
    }

    @Tag(name = "Recommendations")
    @GetMapping("/public/products/{productId}/similar")
    public ResponseEntity<List<ProductDTO>> getSimilarProducts(
            @PathVariable Long productId,
            @RequestParam(name = "limit", defaultValue = "4", required = false) int limit) {
        List<ProductDTO> similar = recommendationService.getSimilarProducts(productId, limit);
        return ResponseEntity.ok(similar);
    }

    @Tag(name = "Recommendations")
    @GetMapping("/public/products/{productId}/frequently-bought-together")
    public ResponseEntity<List<ProductDTO>> getFrequentlyBoughtTogether(
            @PathVariable Long productId,
            @RequestParam(name = "limit", defaultValue = "4", required = false) int limit) {
        List<ProductDTO> recommendations = recommendationService.getFrequentlyBoughtTogether(productId, limit);
        return ResponseEntity.ok(recommendations);
    }
}
