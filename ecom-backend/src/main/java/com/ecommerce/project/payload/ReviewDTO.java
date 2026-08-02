package com.ecommerce.project.payload;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ReviewDTO {
    private Long reviewId;
    private Long productId;
    private String productName;
    private String username;
    private Integer rating;
    private String comment;
    private String createdAt;
}
