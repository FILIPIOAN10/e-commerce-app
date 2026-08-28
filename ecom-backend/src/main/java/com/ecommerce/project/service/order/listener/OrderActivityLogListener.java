package com.ecommerce.project.service.order.listener;

import com.ecommerce.project.config.AsyncConfig;
import com.ecommerce.project.service.UserActivityLogService;
import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes the audit-log entry for a placed order. Runs {@code AFTER_COMMIT} on the
 * bounded async pool ({@link AsyncConfig}) for the same reasons as
 * {@link OrderEmailListener}.
 */
@Component
public class OrderActivityLogListener {

    private final UserActivityLogService userActivityLogService;

    public OrderActivityLogListener(UserActivityLogService userActivityLogService) {
        this.userActivityLogService = userActivityLogService;
    }

    @Async(AsyncConfig.ORDER_EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        userActivityLogService.log(event.email(), "PLACE_ORDER",
                "Order " + event.orderId() + " placed for $" + event.totalAmount());
    }
}
