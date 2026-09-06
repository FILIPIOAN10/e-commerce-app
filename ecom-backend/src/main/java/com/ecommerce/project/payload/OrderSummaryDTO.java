package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingCost;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private List<String> appliedCoupons = new ArrayList<>();
    private String email;
    private Long addressId;

    // The same figures in the currency the customer chose. Always populated: for
    // a base-currency (USD) checkout these mirror the USD amounts, rate 1, so the
    // storefront can render one block unconditionally.
    private String currencyCode = "USD";
    private BigDecimal exchangeRate = BigDecimal.ONE;
    private BigDecimal subtotalInCurrency;
    private BigDecimal discountInCurrency;
    private BigDecimal shippingInCurrency;
    private BigDecimal taxInCurrency;
    private BigDecimal totalInCurrency;
}
