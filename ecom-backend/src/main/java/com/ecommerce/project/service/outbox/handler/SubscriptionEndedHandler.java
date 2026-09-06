package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.SubscriptionNoticeOutboxPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tells the customer their subscription has ended and will not renew. */
@Component
@RequiredArgsConstructor
public class SubscriptionEndedHandler implements OutboxHandler {

    private final EmailService emailService;
    private final NotificationService notificationService;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    public String eventType() {
        return OutboxEventTypes.SUBSCRIPTION_ENDED;
    }

    @Override
    public void handle(String payload) {
        SubscriptionNoticeOutboxPayload data =
                payloadCodec.deserialize(payload, SubscriptionNoticeOutboxPayload.class);
        notificationService.notifyUser(data.email(), "Subscription ended",
                "Your " + data.planName() + " subscription has ended and will no longer renew.",
                "SUBSCRIPTION_ENDED");
        emailService.sendSubscriptionEndedEmail(data.email(), data.planName());
    }
}
