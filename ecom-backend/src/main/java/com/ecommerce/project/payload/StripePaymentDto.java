package com.ecommerce.project.payload;

import com.ecommerce.project.model.Address;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

@Data
public class StripePaymentDto {
    @NotNull
    @Positive
    private Long amount;
    // No currency field: the charge is always StripeService.SETTLEMENT_CURRENCY.
    // A client-chosen one let an order be paid in a weaker unit for the same number.

    @NotBlank
    @Email
    private String email;

    private String name;
    private Address address;
    private String description;
    private Map<String,String> metadata;
    private List<String> couponCodes;
}
