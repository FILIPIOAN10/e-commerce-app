package com.ecommerce.project.service.subscription;

import com.stripe.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes a Stripe event to the {@link SubscriptionEventHandler} that claims its
 * type. An unclaimed type is logged and ignored — {@code StripeWebhookServiceImpl}
 * hands every otherwise-unhandled event here, so most calls are no-ops.
 */
@Slf4j
@Component
public class SubscriptionEventDispatcher {

    private final Map<String, SubscriptionEventHandler> byType = new HashMap<>();

    public SubscriptionEventDispatcher(List<SubscriptionEventHandler> handlers) {
        for (SubscriptionEventHandler handler : handlers) {
            for (String type : handler.eventTypes()) {
                SubscriptionEventHandler existing = byType.putIfAbsent(type, handler);
                if (existing != null) {
                    throw new IllegalStateException(
                            "Two SubscriptionEventHandler beans claim event type " + type);
                }
            }
        }
    }

    public void dispatch(Event event) {
        SubscriptionEventHandler handler = byType.get(event.getType());
        if (handler == null) {
            log.debug("No subscription handler for Stripe event type {}", event.getType());
            return;
        }
        handler.handle(event);
    }
}
