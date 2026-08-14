package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {
    private Double subtotal;
    private Double discountAmount;
    private Double shippingCost;
    private Double totalAmount;
    private List<String> appliedCoupons = new ArrayList<>();
    private String email;
    private Long addressId;
}
