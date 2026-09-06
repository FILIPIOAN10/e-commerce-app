package com.ecommerce.project.service.subscription.handler;

import com.ecommerce.project.service.subscription.SubscriptionEventHandler;
import com.ecommerce.project.service.subscription.SubscriptionLifecycleService;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.periodEndEpochSeconds;
import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.periodStartEpochSeconds;
import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.sessionOf;

/** {@code checkout.session.completed} — the customer finished paying; activate the pending subscription. */
@Slf4j
@Component
@RequiredArgsConstructor
class CheckoutCompletedHandler implements SubscriptionEventHandler {

    private final SubscriptionLifecycleService lifecycle;

    @Override
    public Set<String> eventTypes() {
        return Set.of("checkout.session.completed");
    }

    @Override
    public void handle(Event event) {
        Session session = sessionOf(event);
        if (session == null || session.getSubscription() == null) {
            log.debug("checkout.session.completed with no subscription — not a subscription checkout");
            return;
        }
        Subscription expanded = session.getSubscriptionObject();
        lifecycle.activateFromCheckout(
                session.getId(),
                session.getSubscription(),
                periodStartEpochSeconds(expanded),
                periodEndEpochSeconds(expanded));
    }
}
