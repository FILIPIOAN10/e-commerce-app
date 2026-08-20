package com.ecommerce.project.controller;

import com.ecommerce.project.payload.PaginationParams;
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
public class WishlistController extends BaseController {

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
    public ResponseEntity<ProductResponse> getWishlist(@ModelAttribute PaginationParams params) {
        return ok(wishlistService.getWishlist(params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder()));
    }
}
