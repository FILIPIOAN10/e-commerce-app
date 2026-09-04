package com.ecommerce.project.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code (Integer) requestBody.get("code")} unboxed a null into an NPE whenever
 * the field was missing — a 500 on the login path.
 */
public record TwoFactorLoginRequest(
        @NotNull(message = "Verification code is required")
        Integer code,

        @NotBlank(message = "Challenge token is required")
        String jwtToken) {
}
