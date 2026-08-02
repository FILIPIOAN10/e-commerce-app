package com.ecommerce.project.payload;

import lombok.Data;

import java.util.List;

@Data
public class ReviewResponse {
    private List<ReviewDTO> content;
    private Double averageRating;
    private Long totalReviews;
}
