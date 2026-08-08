package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String adminNote;
    private Double refundAmount;
}
