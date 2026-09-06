package com.ecommerce.project.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A dispute as the admin screen sees it. {@code evidenceFiles} is populated on
 * the detail view and left null on the list.
 */
public record DisputeDTO(
        Long id,
        String stripeDisputeId,
        String paymentIntentId,
        Long orderId,
        BigDecimal amount,
        String currency,
        String reason,
        String status,
        String stripeStatus,
        boolean terminal,
        LocalDateTime evidenceDueBy,
        LocalDateTime evidenceSubmittedAt,
        String outcomeNote,
        Instant createdAt,
        Instant updatedAt,
        List<DisputeEvidenceFileDTO> evidenceFiles) {
}
