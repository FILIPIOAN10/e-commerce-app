package com.ecommerce.project.service;

import com.ecommerce.project.model.AdminAuditLog;

import java.math.BigDecimal;
import java.util.List;

public interface AdminAuditLogService {

    AdminAuditLog logPriceChange(Long adminUserId, String adminUsername, Long productId,
                                 BigDecimal oldPrice, BigDecimal newPrice,
                                 BigDecimal oldSpecialPrice, BigDecimal newSpecialPrice);

    AdminAuditLog logRoleChange(Long adminUserId, String adminUsername, Long userId,
                                String oldRoles, String newRoles);

    AdminAuditLog logAccountUnlock(Long adminUserId, String adminUsername, Long userId, String targetUsername);

    /**
     * Records that an account was erased under GDPR Art. 17. Takes only the
     * pseudonym the erasure assigned — an audit row naming the person we just
     * promised to forget would undo the erasure it is meant to evidence.
     */
    AdminAuditLog logGdprErasure(Long userId, String pseudonym);

    List<AdminAuditLog> getRecentLogs();

    List<AdminAuditLog> getLogsByAction(String action);

    List<AdminAuditLog> getLogsByEntity(String entityType, String entityId);
}
