package com.ecommerce.project.service.subscription;

import com.stripe.model.Event;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionEventDispatcherTest {

    /** Records the events it was handed so a test can assert routing. */
    private static final class RecordingHandler implements SubscriptionEventHandler {
        private final Set<String> types;
        final List<Event> handled = new ArrayList<>();

        RecordingHandler(String... types) {
            this.types = Set.of(types);
        }

        @Override
        public Set<String> eventTypes() {
            return types;
        }

        @Override
        public void handle(Event event) {
            handled.add(event);
        }
    }

    private static Event eventOfType(String type) {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        return event;
    }

    @Test
    void routesEventToTheHandlerThatClaimsItsType() {
        RecordingHandler checkout = new RecordingHandler("checkout.session.completed");
        RecordingHandler invoice = new RecordingHandler("invoice.paid", "invoice.payment_succeeded");
        SubscriptionEventDispatcher dispatcher =
                new SubscriptionEventDispatcher(List.of(checkout, invoice));

        Event paid = eventOfType("invoice.paid");
        dispatcher.dispatch(paid);

        assertThat(invoice.handled).containsExactly(paid);
        assertThat(checkout.handled).isEmpty();
    }

    @Test
    void unclaimedEventTypeIsIgnored() {
        RecordingHandler checkout = new RecordingHandler("checkout.session.completed");
        SubscriptionEventDispatcher dispatcher =
                new SubscriptionEventDispatcher(List.of(checkout));

        dispatcher.dispatch(eventOfType("charge.refunded"));

        assertThat(checkout.handled).isEmpty();
    }

    @Test
    void twoHandlersClaimingTheSameTypeFailFastAtStartup() {
        RecordingHandler a = new RecordingHandler("customer.subscription.updated");
        RecordingHandler b = new RecordingHandler("customer.subscription.updated");

        assertThatThrownBy(() -> new SubscriptionEventDispatcher(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("customer.subscription.updated");
    }
}
