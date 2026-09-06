package com.ecommerce.project.service.subscription.handler;

import com.ecommerce.project.service.subscription.SubscriptionEventHandler;
import com.ecommerce.project.service.subscription.SubscriptionLifecycleService;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.invoiceOf;
import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.subscriptionIdOf;

/**
 * {@code invoice.payment_succeeded} / {@code invoice.paid} — a subscription
 * invoice was paid. On a renewal this extends the period; if the subscription
 * was PAST_DUE, a successful charge brings it back to ACTIVE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InvoicePaidHandler implements SubscriptionEventHandler {

    private final SubscriptionLifecycleService lifecycle;

    @Override
    public Set<String> eventTypes() {
        return Set.of("invoice.payment_succeeded", "invoice.paid");
    }

    @Override
    public void handle(Event event) {
        Invoice invoice = invoiceOf(event);
        String subscriptionId = subscriptionIdOf(invoice);
        if (subscriptionId == null) {
            return; // not a subscription invoice (one-off, quote, ...)
        }
        lifecycle.renewed(subscriptionId, invoice.getPeriodEnd());
    }
}
