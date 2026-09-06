package com.ecommerce.project.payload;

import lombok.Data;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PromoCampaignDTO {
    private Long id;
    @NotBlank
    private String name;
    // Over 100 makes Money.percentage(100 - p) negative, so the campaign
    // would price the product below zero.
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal discountPercent;
    // LocalDateTime.parse(null, ...) throws NPE inside the service.
    @NotBlank
    private String startTime;
    @NotBlank
    private String endTime;
    private Boolean active;
    private List<Long> productIds;
}
