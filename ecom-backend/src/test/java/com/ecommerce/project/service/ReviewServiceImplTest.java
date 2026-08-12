package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ReviewDTO;
import com.ecommerce.project.payload.ReviewResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.ReviewRepository;
import com.ecommerce.project.service.impl.ReviewServiceImpl;
import com.ecommerce.project.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReviewServiceImpl tests")
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AuthUtil authUtil;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User user;
    private Product product;
    private Review review;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(1L);
        user.setUserName("user1");
        user.setEmail("user1@test.com");

        product = new Product();
        product.setProductId(1L);
        product.setProductName("Wireless Headphones");

        review = Review.builder()
                .id(1L)
                .user(user)
                .product(product)
                .rating(4)
                .comment("Great product")
                .createdAt(LocalDateTime.of(2026, 8, 12, 10, 0))
                .build();
    }

    @Test
    @DisplayName("addReview saves review when user purchased the product")
    void addReview_success() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserAndProduct(user, product)).thenReturn(false);
        when(reviewRepository.hasUserPurchasedProduct(user.getEmail(), product)).thenReturn(true);

        String result = reviewService.addReview(1L, 4, "Great product");

        assertEquals("Review added successfully", result);
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("addReview throws when product not found")
    void addReview_productNotFound_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.addReview(99L, 4, "Great product"));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("addReview throws when user already reviewed the product")
    void addReview_alreadyReviewed_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserAndProduct(user, product)).thenReturn(true);

        APIException exception = assertThrows(APIException.class,
                () -> reviewService.addReview(1L, 4, "Great product"));
        assertTrue(exception.getMessage().contains("already reviewed"));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("addReview throws when user has not purchased the product")
    void addReview_notPurchased_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserAndProduct(user, product)).thenReturn(false);
        when(reviewRepository.hasUserPurchasedProduct(user.getEmail(), product)).thenReturn(false);

        APIException exception = assertThrows(APIException.class,
                () -> reviewService.addReview(1L, 4, "Great product"));
        assertTrue(exception.getMessage().contains("only review products you have purchased"));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("addReview throws when rating is out of range")
    void addReview_invalidRating_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserAndProduct(user, product)).thenReturn(false);
        when(reviewRepository.hasUserPurchasedProduct(user.getEmail(), product)).thenReturn(true);

        assertThrows(APIException.class, () -> reviewService.addReview(1L, 0, "Bad"));
        assertThrows(APIException.class, () -> reviewService.addReview(1L, 6, "Bad"));
    }

    @Test
    @DisplayName("updateReview modifies existing review")
    void updateReview_success() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.findByUserAndProduct(user, product)).thenReturn(Optional.of(review));

        String result = reviewService.updateReview(1L, 5, "Updated comment");

        assertEquals("Review updated successfully", result);
        assertEquals(5, review.getRating());
        assertEquals("Updated comment", review.getComment());
        verify(reviewRepository).save(review);
    }

    @Test
    @DisplayName("updateReview throws when review not found")
    void updateReview_notFound_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.findByUserAndProduct(user, product)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> reviewService.updateReview(1L, 5, "Updated"));
    }

    @Test
    @DisplayName("deleteReview removes review")
    void deleteReview_success() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.findByUserAndProduct(user, product)).thenReturn(Optional.of(review));

        String result = reviewService.deleteReview(1L);

        assertEquals("Review deleted successfully", result);
        verify(reviewRepository).delete(review);
    }

    @Test
    @DisplayName("getProductReviews returns paginated reviews with average and count")
    void getProductReviews_success() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Review> page = new PageImpl<>(List.of(review), pageable, 1);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.findByProduct(product, pageable)).thenReturn(page);
        when(reviewRepository.getAverageRatingForProduct(product)).thenReturn(4.5);
        when(reviewRepository.countByProduct(product)).thenReturn(1L);

        ReviewResponse response = reviewService.getProductReviews(1L, 0, 10, "createdAt", "desc");

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(1L, response.getContent().get(0).getReviewId());
        assertEquals(4.5, response.getAverageRating());
        assertEquals(1L, response.getTotalReviews());
    }
}
