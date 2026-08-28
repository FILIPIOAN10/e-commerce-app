package com.ecommerce.project.service.order.event;

import com.ecommerce.project.payload.OrderDTO;

import java.math.BigDecimal;

/**
 * Published after an order is committed (customer checkout or guest checkout).
 * <p>
 * Carries plain facts and a finished {@link OrderDTO} rather than a JPA entity,
 * so listeners can run on another thread without hitting
 * {@code LazyInitializationException}.
 */
public record OrderPlacedEvent(String email, Long orderId, BigDecimal totalAmount, OrderDTO orderDTO) {
}
