package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.CartReminderOutboxPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sends one abandoned-cart reminder email. At-least-once: the {@code
 * (cart, stage)} row is written before the event is enqueued, so a retry here
 * re-sends the same stage's email at worst — it cannot create a new stage.
 */
@Component
@RequiredArgsConstructor
public class CartAbandonmentReminderHandler implements OutboxHandler {

    private final EmailService emailService;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.CART_ABANDONMENT_REMINDER;
    }

    @Override
    public void handle(String payload) {
        CartReminderOutboxPayload data = payloadCodec.deserialize(payload, CartReminderOutboxPayload.class);
        emailService.sendCartRecoveryEmail(
                data.recipientEmail(), data.recipientName(), data.itemCount(), data.cartTotal(), data.recoveryUrl());
    }
}
