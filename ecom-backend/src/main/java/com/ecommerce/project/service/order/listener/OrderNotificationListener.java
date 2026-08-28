package com.ecommerce.project.service.order.listener;

import com.ecommerce.project.config.AsyncConfig;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import com.ecommerce.project.service.order.event.OrderStatusUpdatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes in-app notifications for order events: admins on a new order, the
 * customer on a status change. Runs {@code AFTER_COMMIT} on the bounded async
 * pool ({@link AsyncConfig}) for the same reasons as {@link OrderEmailListener}.
 */
@Component
public class OrderNotificationListener {

    private final NotificationService notificationService;

    public OrderNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async(AsyncConfig.ORDER_EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationService.notifyAdminNewOrder(event.orderId(), event.email(), event.totalAmount());
    }

    @Async(AsyncConfig.ORDER_EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        notificationService.notifyUserOrderStatusChanged(event.orderId(), event.email(), event.status());
    }
}
