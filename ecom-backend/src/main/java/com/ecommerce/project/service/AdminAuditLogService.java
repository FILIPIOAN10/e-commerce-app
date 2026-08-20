package com.ecommerce.project.service;

import com.ecommerce.project.model.AdminAuditLog;

import java.util.List;

public interface AdminAuditLogService {

    AdminAuditLog logPriceChange(Long adminUserId, String adminUsername, Long productId,
                                 double oldPrice, double newPrice,
                                 double oldSpecialPrice, double newSpecialPrice);

    AdminAuditLog logRoleChange(Long adminUserId, String adminUsername, Long userId,
                                String oldRoles, String newRoles);

    AdminAuditLog logAccountUnlock(Long adminUserId, String adminUsername, Long userId, String targetUsername);

    List<AdminAuditLog> getRecentLogs();

    List<AdminAuditLog> getLogsByAction(String action);

    List<AdminAuditLog> getLogsByEntity(String entityType, String entityId);
}
