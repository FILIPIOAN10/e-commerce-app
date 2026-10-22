package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.ReviewVote;
import com.ecommerce.project.model.User;
import com.ecommerce.project.model.VoteType;
import com.ecommerce.project.payload.ReviewDTO;
import com.ecommerce.project.payload.ReviewResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.ReviewRepository;
import com.ecommerce.project.repository.ReviewVoteRepository;
import com.ecommerce.project.service.ReviewService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import com.ecommerce.project.util.SortWhitelist;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewVoteRepository reviewVoteRepository;
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
        // Flush first: the aggregate is re-derived by a native statement, which
        // does not see rows still sitting in the persistence context.
        reviewRepository.flush();
        productRepository.refreshRatingAggregate(productId);
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
        reviewRepository.flush();
        productRepository.refreshRatingAggregate(productId);
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
        reviewRepository.flush();
        productRepository.refreshRatingAggregate(productId);
        return "Review deleted successfully";
    }

    @Override
    public ReviewResponse getProductReviews(Long productId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                "createdAt", SortWhitelist.REVIEW);

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
        return castVote(reviewId, VoteType.HELPFUL);
    }

    @Override
    @Transactional
    public String markReviewUnhelpful(Long reviewId) {
        return castVote(reviewId, VoteType.UNHELPFUL);
    }

    /**
     * Records the current user's vote on a review, and keeps the two counter
     * columns on {@code reviews} in step.
     *
     * <p>Three cases:
     * <ul>
     *   <li><em>No prior vote.</em> Insert a {@link ReviewVote} row; the
     *       {@code (review_id, user_id)} PK is what stops a second vote from
     *       the same user — the old code kept no per-user record and let a
     *       caller loop the endpoint to inflate the counter without limit.</li>
     *   <li><em>Same vote repeated.</em> Return "already voted" (400): the
     *       counter must not move.</li>
     *   <li><em>Switched vote (helpful → unhelpful or vice versa).</em> Flip
     *       the vote row and shift one from the old counter to the new.</li>
     * </ul>
     *
     * <p>Every counter change is an atomic {@code UPDATE reviews SET
     * helpful_count = helpful_count + :delta}, not a read-modify-write in
     * application code: two concurrent votes cannot lose an increment
     * against each other the way {@code getHelpfulCount() + 1} used to.
     */
    private String castVote(Long reviewId, VoteType desired) {
        // Confirm the review exists so we return 404, not the FK error that
        // an insert would throw a moment later.
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "reviewId", reviewId));

        User user = authUtil.loggedInUser();
        if (user.getUserId().equals(review.getUser().getUserId())) {
            throw new APIException("You cannot vote on your own review");
        }

        Optional<ReviewVote> existing = reviewVoteRepository.findByReviewIdAndUserId(reviewId, user.getUserId());

        if (existing.isPresent()) {
            ReviewVote current = existing.get();
            if (current.getVoteType() == desired) {
                throw new APIException("You have already voted on this review");
            }
            // Switched vote: subtract from the old counter, add to the new.
            // Order doesn't matter — the two UPDATEs are independent rows in
            // the same transaction.
            if (current.getVoteType() == VoteType.HELPFUL) {
                reviewVoteRepository.adjustHelpfulCount(reviewId, -1);
                reviewVoteRepository.adjustUnhelpfulCount(reviewId, +1);
            } else {
                reviewVoteRepository.adjustUnhelpfulCount(reviewId, -1);
                reviewVoteRepository.adjustHelpfulCount(reviewId, +1);
            }
            current.setVoteType(desired);
            current.setUpdatedAt(LocalDateTime.now());
            reviewVoteRepository.save(current);
        } else {
            ReviewVote vote = new ReviewVote(
                    reviewId, user.getUserId(), desired, LocalDateTime.now(), LocalDateTime.now());
            reviewVoteRepository.save(vote);
            if (desired == VoteType.HELPFUL) {
                reviewVoteRepository.adjustHelpfulCount(reviewId, +1);
            } else {
                reviewVoteRepository.adjustUnhelpfulCount(reviewId, +1);
            }
        }

        return desired == VoteType.HELPFUL ? "Marked as helpful" : "Marked as unhelpful";
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
