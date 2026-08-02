package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.service.WishlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/{productId}")
    public ResponseEntity<?> addToWishlist(@PathVariable Long productId) {
        String message = wishlistService.addToWishlist(productId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> removeFromWishlist(@PathVariable Long productId) {
        String message = wishlistService.removeFromWishlist(productId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping
    public ResponseEntity<ProductResponse> getWishlist(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = "createdAt", required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder) {
        return ResponseEntity.ok(wishlistService.getWishlist(pageNumber, pageSize, sortBy, sortOrder));
    }
}
