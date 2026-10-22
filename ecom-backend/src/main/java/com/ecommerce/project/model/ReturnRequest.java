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

    /** CUSTOMER (the My Orders button) or SUPPORTPILOT (filed by the AI agent after an admin approved it). */
    @Column(nullable = false, length = 20)
    private String source = SOURCE_CUSTOMER;

    /** The SupportPilot conversation that asked for this return, so an admin can read why. */
    @Column(name = "support_conversation_id", length = 64)
    private String supportConversationId;

    /** The agent's Idempotency-Key: a retried tool call finds this row instead of creating a second. Unique (partial), see V37. */
    @Column(name = "support_idempotency_key", length = 100)
    private String supportIdempotencyKey;

    public static final String SOURCE_CUSTOMER = "CUSTOMER";
    public static final String SOURCE_SUPPORTPILOT = "SUPPORTPILOT";
}
