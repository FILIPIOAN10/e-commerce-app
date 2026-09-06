package com.ecommerce.project.service.subscription.handler;

import com.ecommerce.project.service.subscription.SubscriptionEventHandler;
import com.ecommerce.project.service.subscription.SubscriptionLifecycleService;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.subscriptionOf;

/**
 * {@code customer.subscription.deleted} — the subscription has ended for good
 * (the customer cancelled, or Stripe stopped retrying a failed renewal). Marks
 * it CANCELED and enqueues the "your subscription has ended" notice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SubscriptionDeletedHandler implements SubscriptionEventHandler {

    private final SubscriptionLifecycleService lifecycle;

    @Override
    public Set<String> eventTypes() {
        return Set.of("customer.subscription.deleted");
    }

    @Override
    public void handle(Event event) {
        Subscription subscription = subscriptionOf(event);
        if (subscription == null || subscription.getId() == null) {
            return;
        }
        lifecycle.ended(subscription.getId());
    }
}
