package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.OrderEmailOutboxPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sends the order status-change email. At-least-once, same reasoning as
 * {@link OrderConfirmationEmailHandler}.
 */
@Component
@RequiredArgsConstructor
public class OrderStatusEmailHandler implements OutboxHandler {

    private final EmailService emailService;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.ORDER_STATUS_EMAIL;
    }

    @Override
    public void handle(String payload) {
        OrderEmailOutboxPayload data = payloadCodec.deserialize(payload, OrderEmailOutboxPayload.class);
        emailService.sendOrderStatusUpdateEmail(data.recipientEmail(), data.order());
    }
}
