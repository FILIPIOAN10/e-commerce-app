package com.ecommerce.project.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleDTO {

    private Long bundleId;

    @NotBlank(message = "Bundle name is required")
    private String name;

    private String description;

    @PositiveOrZero(message = "Bundle discount cannot be negative")
    private Double discountPercentage = 0.0;

    private Boolean active = true;

    private List<ProductDTO> products = new ArrayList<>();

    private Double bundlePrice;
    private Double discountedPrice;
    private Double savings;
}
