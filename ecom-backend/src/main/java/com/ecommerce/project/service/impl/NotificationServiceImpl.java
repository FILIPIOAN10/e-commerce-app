package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.AppNotification;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.NotificationDTO;
import com.ecommerce.project.repository.NotificationRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.ecommerce.project.model.AppRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void notifyAdminNewOrder(Long orderId, String customerEmail, BigDecimal totalAmount) {
        String title = "New Order Received";
        String message = String.format("Order #%d from %s — $%.2f", orderId, customerEmail, totalAmount);

        // Ask the database for the admins rather than loading every user and
        // filtering in Java: User.roles is EAGER, so findAll() was a full table
        // scan plus the role join on the checkout path.
        List<User> admins = userRepository.findAllByRoleName(AppRole.ROLE_ADMIN);

        for (User admin : admins) {
            AppNotification notification = saveNotification(admin.getEmail(), title, message, "NEW_ORDER", orderId);
            sendToUser(admin.getEmail(), notification);
        }
    }

    @Override
    public void notifyAdminRefundFailed(Long orderId, BigDecimal amount, String reason) {
        String title = "Refund needs attention";
        String message = String.format("Automated refund of $%.2f for order #%d failed: %s", amount, orderId, reason);

        List<User> admins = userRepository.findAllByRoleName(AppRole.ROLE_ADMIN);
        for (User admin : admins) {
            AppNotification notification = saveNotification(admin.getEmail(), title, message, "REFUND_FAILED", orderId);
            sendToUser(admin.getEmail(), notification);
        }
    }

    @Override
    public void notifyUser(String email, String title, String message, String type) {
        AppNotification notification = saveNotification(email, title, message, type, null);
        sendToUser(email, notification);
    }

    @Override
    public void notifyUserOrderStatusChanged(Long orderId, String email, String newStatus) {
        String title = "Order Status Updated";
        String message = String.format("Your order #%d is now: %s", orderId, newStatus);

        AppNotification notification = saveNotification(email, title, message, "ORDER_STATUS", orderId);
        sendToUser(email, notification);
    }

    @Override
    public List<NotificationDTO> getNotifications(String email, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AppNotification> notifications = notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email, pageRequest);
        return notifications.stream().map(this::toDTO).toList();
    }

    @Override
    public long getUnreadCount(String email) {
        return notificationRepository.countByRecipientEmailAndReadFalse(email);
    }

    @Override
    public void markAllAsRead(String email) {
        notificationRepository.markAllAsRead(email);
    }

    private AppNotification saveNotification(String email, String title, String message, String type, Long referenceId) {
        AppNotification notification = new AppNotification();
        notification.setRecipientEmail(email);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    private void sendToUser(String email, AppNotification notification) {
        try {
            messagingTemplate.convertAndSendToUser(email, "/queue/notifications", toDTO(notification));
        } catch (Exception e) {
            log.warn("Failed to send WebSocket notification to {}: {}", email, e.getMessage());
        }
    }

    private NotificationDTO toDTO(AppNotification n) {
        return new NotificationDTO(
                n.getId(),
                n.getRecipientEmail(),
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                n.getReferenceId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
