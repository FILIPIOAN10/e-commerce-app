package com.ecommerce.project.service;

import com.ecommerce.project.payload.ReviewResponse;

public interface ReviewService {

    String addReview(Long productId, Integer rating, String comment);

    String updateReview(Long productId, Integer rating, String comment);

    String deleteReview(Long productId);

    String markReviewHelpful(Long reviewId);

    String markReviewUnhelpful(Long reviewId);

    ReviewResponse getProductReviews(Long productId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
