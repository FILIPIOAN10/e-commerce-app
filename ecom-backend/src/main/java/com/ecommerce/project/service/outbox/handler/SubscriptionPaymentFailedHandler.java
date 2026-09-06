package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.SubscriptionNoticeOutboxPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dunning: tells the customer a renewal charge failed. In-app first (never
 * fails), then the email — a throw from the email send lets the outbox retry.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionPaymentFailedHandler implements OutboxHandler {

    private final EmailService emailService;
    private final NotificationService notificationService;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.SUBSCRIPTION_PAYMENT_FAILED;
    }

    @Override
    public void handle(String payload) {
        SubscriptionNoticeOutboxPayload data =
                payloadCodec.deserialize(payload, SubscriptionNoticeOutboxPayload.class);
        notificationService.notifyUser(data.email(), "Subscription payment failed",
                "We couldn't renew your " + data.planName() + " subscription. Please update your card.",
                "SUBSCRIPTION_PAST_DUE");
        emailService.sendSubscriptionPaymentFailedEmail(data.email(), data.planName());
    }
}
