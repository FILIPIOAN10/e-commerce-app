package com.ecommerce.project.payload.request;

import jakarta.validation.constraints.Size;

public record ReturnReasonRequest(
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason) {

    /** Mirrors the previous getOrDefault so an omitted body keeps its meaning. */
    public String reasonOrDefault() {
        return reason == null || reason.isBlank() ? "No reason provided" : reason;
    }
}
