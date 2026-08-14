package com.ecommerce.project.payload;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestCheckoutRequestDTO {

    @NotBlank
    @Email
    private String email;

    @Valid
    private AddressDTO address;

    @NotBlank
    private String paymentMethod;

    private String pgName;
    private String pgPaymentId;
    private String pgStatus;
    private String pgResponseMessage;

    private List<String> couponCodes;

    @NotEmpty
    private List<CartItemDTO> items;
}
