package com.ecommerce.project.service.subscription.handler;

import com.ecommerce.project.service.subscription.SubscriptionEventHandler;
import com.ecommerce.project.service.subscription.SubscriptionLifecycleService;
import com.ecommerce.project.service.subscription.SubscriptionStatus;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.periodEndEpochSeconds;
import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.subscriptionOf;

/**
 * {@code customer.subscription.updated} — the catch-all. Stripe sends this on
 * every renewal, plan change, pause, and cancellation, so it is the reliable
 * place to mirror the status and the current period end.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SubscriptionUpdatedHandler implements SubscriptionEventHandler {

    private final SubscriptionLifecycleService lifecycle;

    @Override
    public Set<String> eventTypes() {
        return Set.of("customer.subscription.updated");
    }

    @Override
    public void handle(Event event) {
        Subscription subscription = subscriptionOf(event);
        if (subscription == null || subscription.getId() == null) {
            return;
        }
        lifecycle.syncFromStripe(
                subscription.getId(),
                SubscriptionStatus.fromStripe(subscription.getStatus()),
                periodEndEpochSeconds(subscription));
    }
}
