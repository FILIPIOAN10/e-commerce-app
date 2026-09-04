package com.ecommerce.project.payload.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Replaces {@code Map<String, Object>}: {@code (Integer) body.get("rating")}
 * threw ClassCastException on {@code 4.5} or {@code "5"} and NPE when absent,
 * all of which surfaced as HTTP 500 for what is plainly a bad request.
 */
public record ReviewRequest(
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @Size(max = 1000, message = "Comment must be at most 1000 characters")
        String comment) {
}
