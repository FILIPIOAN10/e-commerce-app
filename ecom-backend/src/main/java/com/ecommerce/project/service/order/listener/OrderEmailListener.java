package com.ecommerce.project.service.order.listener;

import com.ecommerce.project.config.AsyncConfig;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import com.ecommerce.project.service.order.event.OrderStatusUpdatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the customer-facing order emails.
 * <p>
 * {@code AFTER_COMMIT} so a rolled-back checkout never emails anyone;
 * {@link Async} so a slow SMTP server does not add its timeout to the request
 * that committed the order (bounded pool in {@link AsyncConfig}). A failure here
 * is logged by AsyncConfig's uncaught handler, not retried — durable delivery is
 * the transactional-outbox follow-up.
 */
@Component
public class OrderEmailListener {

    private final EmailService emailService;

    public OrderEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async(AsyncConfig.ORDER_EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailService.sendOrderConfirmationEmail(event.email(), event.orderDTO());
    }

    @Async(AsyncConfig.ORDER_EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        emailService.sendOrderStatusUpdateEmail(event.email(), event.orderDTO());
    }
}
