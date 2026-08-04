package com.ecommerce.project.payload;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    private Long productId;

    @NotBlank(message = "Product name is required")
    @Size(min = 3 ,message = "Product name must contain at least 3 characters")
    private String productName;
    private String image;
    @NotBlank(message = "Product description is required")
    @Size(min = 3 ,message = "Product description must contain at least 3=6 characters")
    private String description;
    private String tags;

    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity cannot be negative")
    private Integer quantity;

    private Integer lowStockThreshold;

    @PositiveOrZero(message = "Price cannot be negative")
    private double price;


    @PositiveOrZero(message = "Discount cannot be negative")
    @DecimalMax(value = "100.0",message = "Discount cannot be greater than 100")
    private double discount;
    @PositiveOrZero(message = "Special price cannot be negative")
    private double specialPrice;
}
