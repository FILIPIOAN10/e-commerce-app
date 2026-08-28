package com.ecommerce.project.service.order.event;

import com.ecommerce.project.payload.OrderDTO;

/**
 * Published after an order's status transition is committed.
 * <p>
 * Carries plain facts and a finished {@link OrderDTO} rather than a JPA entity,
 * so listeners can run on another thread without hitting
 * {@code LazyInitializationException}.
 */
public record OrderStatusUpdatedEvent(Long orderId, String email, String status, OrderDTO orderDTO) {
}
