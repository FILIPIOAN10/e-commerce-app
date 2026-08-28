package com.ecommerce.project.service.order.listener;

import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.UserActivityLogService;
import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import com.ecommerce.project.service.order.event.OrderStatusUpdatedEvent;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.OrderEmailOutboxPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order event listeners")
class OrderListenersTest {

    private static final OrderDTO ORDER_DTO = new OrderDTO();
    private static final OrderPlacedEvent PLACED =
            new OrderPlacedEvent("buyer@example.com", 42L, 99.90, ORDER_DTO);
    private static final OrderStatusUpdatedEvent STATUS_CHANGED =
            new OrderStatusUpdatedEvent(42L, "buyer@example.com", "Shipped", ORDER_DTO);

    @Nested
    @DisplayName("OrderEmailListener")
    class Email {
        @Mock OutboxEventPublisher outboxEventPublisher;

        @Test
        @DisplayName("enqueues a confirmation-email outbox event on OrderPlacedEvent")
        void confirmation() {
            new OrderEmailListener(outboxEventPublisher).onOrderPlaced(PLACED);

            ArgumentCaptor<OrderEmailOutboxPayload> payload = ArgumentCaptor.forClass(OrderEmailOutboxPayload.class);
            verify(outboxEventPublisher).publish(eq(OutboxEventTypes.ORDER_CONFIRMATION_EMAIL), payload.capture());
            assertThat(payload.getValue().recipientEmail()).isEqualTo("buyer@example.com");
            assertThat(payload.getValue().order()).isSameAs(ORDER_DTO);
            verifyNoMoreInteractions(outboxEventPublisher);
        }

        @Test
        @DisplayName("enqueues a status-email outbox event on OrderStatusUpdatedEvent")
        void statusUpdate() {
            new OrderEmailListener(outboxEventPublisher).onOrderStatusUpdated(STATUS_CHANGED);

            ArgumentCaptor<OrderEmailOutboxPayload> payload = ArgumentCaptor.forClass(OrderEmailOutboxPayload.class);
            verify(outboxEventPublisher).publish(eq(OutboxEventTypes.ORDER_STATUS_EMAIL), payload.capture());
            assertThat(payload.getValue().recipientEmail()).isEqualTo("buyer@example.com");
            assertThat(payload.getValue().order()).isSameAs(ORDER_DTO);
            verifyNoMoreInteractions(outboxEventPublisher);
        }
    }

    @Nested
    @DisplayName("OrderNotificationListener")
    class Notifications {
        @Mock NotificationService notificationService;

        @Test
        @DisplayName("notifies admins on OrderPlacedEvent")
        void adminOnPlaced() {
            new OrderNotificationListener(notificationService).onOrderPlaced(PLACED);
            verify(notificationService).notifyAdminNewOrder(42L, "buyer@example.com", 99.90);
            verifyNoMoreInteractions(notificationService);
        }

        @Test
        @DisplayName("notifies the customer on OrderStatusUpdatedEvent")
        void customerOnStatusChange() {
            new OrderNotificationListener(notificationService).onOrderStatusUpdated(STATUS_CHANGED);
            verify(notificationService).notifyUserOrderStatusChanged(42L, "buyer@example.com", "Shipped");
            verifyNoMoreInteractions(notificationService);
        }
    }

    @Nested
    @DisplayName("OrderActivityLogListener")
    class ActivityLog {
        @Mock UserActivityLogService userActivityLogService;

        @Test
        @DisplayName("records a PLACE_ORDER entry on OrderPlacedEvent")
        void logsOnPlaced() {
            new OrderActivityLogListener(userActivityLogService).onOrderPlaced(PLACED);
            verify(userActivityLogService).log("buyer@example.com", "PLACE_ORDER",
                    "Order 42 placed for $99.9");
            verifyNoMoreInteractions(userActivityLogService);
        }
    }
}
