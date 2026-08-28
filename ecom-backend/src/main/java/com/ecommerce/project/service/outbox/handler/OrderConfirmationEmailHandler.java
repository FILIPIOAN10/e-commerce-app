package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.OrderEmailOutboxPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sends the order-confirmation email. At-least-once: a duplicate confirmation is
 * an acceptable outcome (SMTP itself de-duplicates nothing), which is why the
 * email path is the one moved onto the outbox first — a <em>lost</em>
 * confirmation is not acceptable.
 */
@Component
@RequiredArgsConstructor
public class OrderConfirmationEmailHandler implements OutboxHandler {

    private final EmailService emailService;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.ORDER_CONFIRMATION_EMAIL;
    }

    @Override
    public void handle(String payload) {
        OrderEmailOutboxPayload data = payloadCodec.deserialize(payload, OrderEmailOutboxPayload.class);
        emailService.sendOrderConfirmationEmail(data.recipientEmail(), data.order());
    }
}
