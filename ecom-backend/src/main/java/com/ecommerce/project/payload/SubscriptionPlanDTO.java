package com.ecommerce.project.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {

    private Long planId;

    @NotBlank(message = "Plan name is required")
    private String name;

    private String description;

    @NotNull(message = "Product is required")
    private Long productId;

    private String stripeProductId;
    private String stripePriceId;

    @NotBlank(message = "Interval is required")
    private String interval;

    @NotNull
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String currency = "USD";

    private Boolean active = true;
}
