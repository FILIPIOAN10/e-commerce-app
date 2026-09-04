package com.ecommerce.project.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerRequest(
        @NotBlank(message = "Answer is required")
        @Size(max = 2000, message = "Answer must be at most 2000 characters")
        String answer) {
}
