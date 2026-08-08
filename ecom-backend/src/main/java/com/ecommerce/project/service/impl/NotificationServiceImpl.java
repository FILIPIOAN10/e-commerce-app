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

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void notifyAdminNewOrder(Long orderId, String customerEmail, Double totalAmount) {
        String title = "New Order Received";
        String message = String.format("Order #%d from %s — $%.2f", orderId, customerEmail, totalAmount);

        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream()
                        .anyMatch(r -> r.getRoleName().name().equals("ROLE_ADMIN")))
                .toList();

        for (User admin : admins) {
            AppNotification notification = saveNotification(admin.getEmail(), title, message, "NEW_ORDER", orderId);
            sendToUser(admin.getEmail(), notification);
        }
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
