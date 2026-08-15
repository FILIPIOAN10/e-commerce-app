package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ReviewDTO;
import com.ecommerce.project.payload.ReviewResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.ReviewRepository;
import com.ecommerce.project.service.ReviewService;
import com.ecommerce.project.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final AuthUtil authUtil;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    @Transactional
    public String addReview(Long productId, Integer rating, String comment) {
        User user = authUtil.loggedInUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (reviewRepository.existsByUserAndProduct(user, product)) {
            throw new APIException("You have already reviewed this product");
        }

        if (!reviewRepository.hasUserPurchasedProduct(user.getEmail(), product)) {
            throw new APIException("You can only review products you have purchased");
        }

        if (rating < 1 || rating > 5) {
            throw new APIException("Rating must be between 1 and 5");
        }

        Review review = Review.builder()
                .user(user)
                .product(product)
                .rating(rating)
                .comment(comment)
                .verifiedPurchase(true)
                .helpfulCount(0)
                .unhelpfulCount(0)
                .build();

        reviewRepository.save(review);
        return "Review added successfully";
    }

    @Override
    @Transactional
    public String updateReview(Long productId, Integer rating, String comment) {
        User user = authUtil.loggedInUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        Review review = reviewRepository.findByUserAndProduct(user, product)
                .orElseThrow(() -> new APIException("Review not found"));

        if (rating < 1 || rating > 5) {
            throw new APIException("Rating must be between 1 and 5");
        }

        review.setRating(rating);
        review.setComment(comment);
        reviewRepository.save(review);
        return "Review updated successfully";
    }

    @Override
    @Transactional
    public String deleteReview(Long productId) {
        User user = authUtil.loggedInUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        Review review = reviewRepository.findByUserAndProduct(user, product)
                .orElseThrow(() -> new APIException("Review not found"));

        reviewRepository.delete(review);
        return "Review deleted successfully";
    }

    @Override
    public ReviewResponse getProductReviews(Long productId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        Sort sort = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sort);

        Page<Review> reviewPage = reviewRepository.findByProduct(product, pageDetails);

        List<ReviewDTO> reviewDTOs = reviewPage.getContent().stream()
                .map(this::mapToDTO)
                .toList();

        ReviewResponse response = new ReviewResponse();
        response.setContent(reviewDTOs);
        response.setAverageRating(reviewRepository.getAverageRatingForProduct(product));
        response.setTotalReviews(reviewRepository.countByProduct(product));
        return response;
    }

    @Override
    @Transactional
    public String markReviewHelpful(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "reviewId", reviewId));
        review.setHelpfulCount(review.getHelpfulCount() + 1);
        reviewRepository.save(review);
        return "Marked as helpful";
    }

    @Override
    @Transactional
    public String markReviewUnhelpful(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "reviewId", reviewId));
        review.setUnhelpfulCount(review.getUnhelpfulCount() + 1);
        reviewRepository.save(review);
        return "Marked as unhelpful";
    }

    private ReviewDTO mapToDTO(Review review) {
        ReviewDTO dto = new ReviewDTO();
        dto.setReviewId(review.getId());
        dto.setProductId(review.getProduct().getProductId());
        dto.setProductName(review.getProduct().getProductName());
        dto.setUsername(review.getUser().getUserName());
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setVerifiedPurchase(review.getVerifiedPurchase());
        dto.setHelpfulCount(review.getHelpfulCount());
        dto.setUnhelpfulCount(review.getUnhelpfulCount());
        dto.setCreatedAt(review.getCreatedAt().format(FORMATTER));
        return dto;
    }
}
