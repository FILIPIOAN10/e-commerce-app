package com.ecommerce.project.payload;


import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class OrderStatusUpdateDto {
    @NotBlank
    private String status;
}
