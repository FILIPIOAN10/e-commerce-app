package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCheckoutDTO {
    private String checkoutUrl;
    private String sessionId;
    private Long userSubscriptionId;
}
