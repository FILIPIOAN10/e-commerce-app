package com.ecommerce.project.payload;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    private Long productId;

    @NotBlank(message = "Product name is required")
    @Size(min = 3 ,message = "Product name must contain at least 3 characters")
    private String productName;
    private String image;
    private List<String> images = new ArrayList<>();
    @NotBlank(message = "Product description is required")
    @Size(min = 3 ,message = "Product description must contain at least 3=6 characters")
    private String description;
    private String tags;

    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity cannot be negative")
    private Integer quantity;

    private Integer lowStockThreshold;

    @PositiveOrZero(message = "Price cannot be negative")
    private BigDecimal price;


    @PositiveOrZero(message = "Discount cannot be negative")
    @DecimalMax(value = "100.0",message = "Discount cannot be greater than 100")
    private BigDecimal discount;
    @PositiveOrZero(message = "Special price cannot be negative")
    private BigDecimal specialPrice;

    // Non-null only when the caller asked for a non-base currency via the
    // X-Currency header: then price and specialPrice are in this currency, not
    // USD. Null means the figures are in the store base (USD), as before.
    private String currency;

    private Double averageRating;
    private Long reviewCount;
    private String categoryName;
    private Long categoryId;
    private Long cartItemId;
    private Boolean savedForLater;
}
