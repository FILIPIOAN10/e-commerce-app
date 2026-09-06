package com.ecommerce.project.service.subscription;

import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;

/**
 * Defensive extraction from Stripe webhook objects. The subscription id and the
 * period end moved around between stripe-java majors (period end is on the
 * subscription <em>item</em> now, the subscription reference on the invoice is
 * under {@code parent.subscription_details}); these helpers try the current
 * shape and fall back rather than NPE on a payload variant.
 */
public final class StripeSubscriptionEvents {

    private StripeSubscriptionEvents() {
    }

    public static <T> T objectOf(Event event, Class<T> type) {
        return event.getDataObjectDeserializer().getObject()
                .filter(type::isInstance)
                .map(type::cast)
                .orElse(null);
    }

    /** The Stripe subscription id an invoice belongs to, or {@code null} if it is not a subscription invoice. */
    public static String subscriptionIdOf(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        if (invoice.getParent() != null && invoice.getParent().getSubscriptionDetails() != null) {
            String id = invoice.getParent().getSubscriptionDetails().getSubscription();
            if (id != null) {
                return id;
            }
        }
        if (invoice.getLines() != null && invoice.getLines().getData() != null) {
            return invoice.getLines().getData().stream()
                    .map(line -> line.getSubscription())
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /** Current-period-end epoch seconds for a subscription, read from its first item. */
    public static Long periodEndEpochSeconds(Subscription subscription) {
        if (subscription == null || subscription.getItems() == null
                || subscription.getItems().getData() == null || subscription.getItems().getData().isEmpty()) {
            return null;
        }
        return subscription.getItems().getData().get(0).getCurrentPeriodEnd();
    }

    public static Long periodStartEpochSeconds(Subscription subscription) {
        if (subscription == null || subscription.getItems() == null
                || subscription.getItems().getData() == null || subscription.getItems().getData().isEmpty()) {
            return subscription != null ? subscription.getStartDate() : null;
        }
        return subscription.getItems().getData().get(0).getCurrentPeriodStart();
    }

    public static Session sessionOf(Event event) {
        return objectOf(event, Session.class);
    }

    public static Invoice invoiceOf(Event event) {
        return objectOf(event, Invoice.class);
    }

    public static Subscription subscriptionOf(Event event) {
        return objectOf(event, Subscription.class);
    }
}
