package com.ecommerce.project.payload;

import com.ecommerce.project.model.StockMovement;

import java.time.Instant;

/**
 * One row of the stock history as an admin reads it: what moved, why, what it
 * left behind, and who did it.
 */
public record StockMovementDTO(
        Long id,
        Long productId,
        int delta,
        String reason,
        String refType,
        Long refId,
        int balanceAfter,
        String note,
        String createdBy,
        Instant createdAt) {

    public static StockMovementDTO from(StockMovement movement) {
        return new StockMovementDTO(
                movement.getId(),
                movement.getProductId(),
                movement.getDelta(),
                movement.getReason().name(),
                movement.getRefType(),
                movement.getRefId(),
                movement.getBalanceAfter(),
                movement.getNote(),
                movement.getCreatedBy(),
                movement.getCreatedAt());
    }
}
