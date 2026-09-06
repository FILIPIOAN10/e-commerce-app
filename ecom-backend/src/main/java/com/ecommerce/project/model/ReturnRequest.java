package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
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

    // The refund owed for this return — copied from the order's total, which is
    // NUMERIC(12,2), so this is too rather than a Double that widens it. See V30.
    @Column(precision = 12, scale = 2)
    private BigDecimal refundAmount;
}
