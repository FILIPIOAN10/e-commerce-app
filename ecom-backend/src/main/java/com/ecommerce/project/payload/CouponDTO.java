package com.ecommerce.project.payload;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CouponDTO {
    private Long couponId;
    @NotBlank
    @Size(min = 3, max = 30)
    private String code;
    @NotNull
    @Min(1)
    @Max(100)
    private Integer discountPercent;
    @NotNull
    private LocalDate expiryDate;
    @NotNull
    @Min(1)
    private Integer maxUses;
    private Integer usedCount;
    private Boolean active;
}
