package com.ecommerce.project.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackingRequest(
        @NotBlank(message = "Carrier name is required")
        @Size(max = 100, message = "Carrier name must be at most 100 characters")
        String carrierName,

        @NotBlank(message = "Tracking number is required")
        @Size(max = 100, message = "Tracking number must be at most 100 characters")
        String trackingNumber) {
}
