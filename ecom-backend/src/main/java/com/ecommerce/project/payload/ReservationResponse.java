package com.ecommerce.project.payload;

public record ReservationResponse(
        String reservationId,
        Long productId,
        Integer quantity,
        Long expiresAt
) {
}
