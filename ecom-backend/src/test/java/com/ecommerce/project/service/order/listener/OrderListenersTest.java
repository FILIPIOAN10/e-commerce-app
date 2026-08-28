package com.ecommerce.project.service.order.listener;

import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.UserActivityLogService;
import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import com.ecommerce.project.service.order.event.OrderStatusUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        @Mock EmailService emailService;

        @Test
        @DisplayName("sends the confirmation email on OrderPlacedEvent")
        void confirmation() {
            new OrderEmailListener(emailService).onOrderPlaced(PLACED);
            verify(emailService).sendOrderConfirmationEmail("buyer@example.com", ORDER_DTO);
            verifyNoMoreInteractions(emailService);
        }

        @Test
        @DisplayName("sends the status email on OrderStatusUpdatedEvent")
        void statusUpdate() {
            new OrderEmailListener(emailService).onOrderStatusUpdated(STATUS_CHANGED);
            verify(emailService).sendOrderStatusUpdateEmail("buyer@example.com", ORDER_DTO);
            verifyNoMoreInteractions(emailService);
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
