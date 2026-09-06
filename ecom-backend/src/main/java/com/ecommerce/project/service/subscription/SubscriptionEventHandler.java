package com.ecommerce.project.service.subscription;

import com.stripe.model.Event;

import java.util.Set;

/**
 * Handles one or more Stripe subscription event types. Each implementation is a
 * {@code @Component}; {@link SubscriptionEventDispatcher} builds the routing map
 * from {@link #eventTypes()} at startup — the same Strategy-registry shape as
 * {@code OutboxHandlerRegistry} and {@code PaymentGatewayRegistry}.
 *
 * <p>Runs inside the webhook's transaction, after
 * {@code StripeWebhookServiceImpl} has already verified the signature and claimed
 * the event id, so a redelivery never reaches here twice.
 */
public interface SubscriptionEventHandler {

    /** The Stripe event {@code type} strings this handler consumes. */
    Set<String> eventTypes();

    void handle(Event event);
}
