package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ReviewResponse;
import com.ecommerce.project.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/{productId}")
    public ResponseEntity<?> addReview(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body) {
        Integer rating = (Integer) body.get("rating");
        String comment = (String) body.get("comment");
        String message = reviewService.addReview(productId, rating, comment);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<?> updateReview(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body) {
        Integer rating = (Integer) body.get("rating");
        String comment = (String) body.get("comment");
        String message = reviewService.updateReview(productId, rating, comment);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> deleteReview(@PathVariable Long productId) {
        String message = reviewService.deleteReview(productId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/{reviewId}/helpful")
    public ResponseEntity<?> markHelpful(@PathVariable Long reviewId) {
        String message = reviewService.markReviewHelpful(reviewId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/{reviewId}/unhelpful")
    public ResponseEntity<?> markUnhelpful(@PathVariable Long reviewId) {
        String message = reviewService.markReviewUnhelpful(reviewId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ReviewResponse> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(name = "pageNumber", defaultValue = "0", required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "10", required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = "createdAt", required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = "desc", required = false) String sortOrder) {
        return ResponseEntity.ok(reviewService.getProductReviews(productId, pageNumber, pageSize, sortBy, sortOrder));
    }
}
