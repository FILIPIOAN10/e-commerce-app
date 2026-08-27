package com.ecommerce.project.controller;

import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.payload.ReviewResponse;
import com.ecommerce.project.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController extends BaseController {

    private final ReviewService reviewService;

    @PostMapping("/users/reviews/{productId}")
    public ResponseEntity<?> addReview(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body) {
        Integer rating = (Integer) body.get("rating");
        String comment = (String) body.get("comment");
        String message = reviewService.addReview(productId, rating, comment);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PutMapping("/users/reviews/{productId}")
    public ResponseEntity<?> updateReview(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body) {
        Integer rating = (Integer) body.get("rating");
        String comment = (String) body.get("comment");
        String message = reviewService.updateReview(productId, rating, comment);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @DeleteMapping("/users/reviews/{productId}")
    public ResponseEntity<?> deleteReview(@PathVariable Long productId) {
        String message = reviewService.deleteReview(productId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/users/reviews/{reviewId}/helpful")
    public ResponseEntity<?> markHelpful(@PathVariable Long reviewId) {
        String message = reviewService.markReviewHelpful(reviewId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/users/reviews/{reviewId}/unhelpful")
    public ResponseEntity<?> markUnhelpful(@PathVariable Long reviewId) {
        String message = reviewService.markReviewUnhelpful(reviewId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping({"/users/reviews/{productId}", "/public/products/{productId}/reviews"})
    public ResponseEntity<ReviewResponse> getProductReviews(
            @PathVariable Long productId,
            @ModelAttribute PaginationParams params) {
        return ok(reviewService.getProductReviews(productId, params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder()));
    }
}
