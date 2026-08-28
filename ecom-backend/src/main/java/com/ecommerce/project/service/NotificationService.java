package com.ecommerce.project.service;

import com.ecommerce.project.payload.NotificationDTO;

import java.math.BigDecimal;
import java.util.List;

public interface NotificationService {

    void notifyAdminNewOrder(Long orderId, String customerEmail, BigDecimal totalAmount);

    void notifyUserOrderStatusChanged(Long orderId, String email, String newStatus);

    List<NotificationDTO> getNotifications(String email, int page, int size);

    long getUnreadCount(String email);

    void markAllAsRead(String email);
}
