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
 * {@code invoice.payment_failed} — a renewal charge failed. Marks the
 * subscription PAST_DUE and enqueues a dunning notice; Stripe keeps retrying on
 * its own schedule and will send {@code customer.subscription.deleted} if it
 * eventually gives up.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InvoicePaymentFailedHandler implements SubscriptionEventHandler {

    private final SubscriptionLifecycleService lifecycle;

    @Override
    public Set<String> eventTypes() {
        return Set.of("invoice.payment_failed");
    }

    @Override
    public void handle(Event event) {
        Invoice invoice = invoiceOf(event);
        String subscriptionId = subscriptionIdOf(invoice);
        if (subscriptionId == null) {
            return;
        }
        lifecycle.paymentFailed(subscriptionId, invoice.getNextPaymentAttempt());
    }
}
