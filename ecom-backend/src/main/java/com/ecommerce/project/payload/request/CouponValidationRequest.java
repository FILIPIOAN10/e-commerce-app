package com.ecommerce.project.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * {@code ((Number) body.get("orderAmount")).doubleValue()} was an NPE whenever
 * the field was absent and a ClassCastException whenever it arrived as a string
 * — both 500s. BigDecimal rather than Double because this feeds the pricing
 * pipeline, which is decimal end to end.
 */
public record CouponValidationRequest(
        @NotBlank(message = "Coupon code is required")
        String code,

        @NotNull(message = "Order amount is required")
        @DecimalMin(value = "0.0", message = "Order amount cannot be negative")
        BigDecimal orderAmount) {
}
