package com.ecommerce.project.payload;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long orderId;
    private String email;

    private List<OrderItemDTO> items;
    private LocalDate orderDate;
    private PaymentDTO payment;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal shippingCost;
    private BigDecimal taxAmount;
    private List<String> appliedCoupons = new ArrayList<>();
    private String orderStatus;
    private Long addressId;

    // The presentation currency the customer checked out in and the USD -> that
    // rate frozen at checkout. The amounts above stay in USD; multiply by the
    // rate to reproduce what the customer was shown. "USD" / 1 for every order
    // placed before multi-currency, and for base-currency checkouts.
    private String currencyCode = "USD";
    private BigDecimal exchangeRate = BigDecimal.ONE;
}
