package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestDTO {
    private Long id;
    private Long orderId;
    private String userEmail;
    private String reason;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private LocalDateTime shippedBackAt;
    private String adminNote;
    private String trackingNumber;
    private String carrierName;
    private String trackingStatus;
    private LocalDateTime lastTrackedAt;
    private BigDecimal refundAmount;
}
