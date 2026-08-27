package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "return_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private String status;

    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    private LocalDateTime shippedBackAt;

    private String adminNote;

    private String trackingNumber;

    private String carrierName;

    private String trackingStatus;

    private LocalDateTime lastTrackedAt;

    private Double refundAmount;
}
