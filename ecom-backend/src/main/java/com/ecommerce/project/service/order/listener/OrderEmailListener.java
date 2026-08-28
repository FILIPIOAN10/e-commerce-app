package com.ecommerce.project.service.order.listener;

import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import com.ecommerce.project.service.order.event.OrderStatusUpdatedEvent;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.OrderEmailOutboxPayload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Enqueues the customer-facing order emails onto the transactional outbox.
 *
 * <p>{@code BEFORE_COMMIT} and synchronous (not {@code @Async}): the outbox row
 * must be written in the same transaction that commits the order, so it commits
 * with the order or not at all. Actual delivery — with retry and dead-lettering
 * if SMTP is down — is done later by
 * {@link com.ecommerce.project.service.outbox.OutboxDispatcher}.
 */
@Component
public class OrderEmailListener {

    private final OutboxEventPublisher outboxEventPublisher;

    public OrderEmailListener(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        outboxEventPublisher.publish(OutboxEventTypes.ORDER_CONFIRMATION_EMAIL,
                new OrderEmailOutboxPayload(event.email(), event.orderDTO()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        outboxEventPublisher.publish(OutboxEventTypes.ORDER_STATUS_EMAIL,
                new OrderEmailOutboxPayload(event.email(), event.orderDTO()));
    }
}
