package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.AdminAuditLog;
import com.ecommerce.project.repository.AdminAuditLogRepository;
import com.ecommerce.project.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog logPriceChange(Long adminUserId, String adminUsername, Long productId,
                                        double oldPrice, double newPrice,
                                        double oldSpecialPrice, double newSpecialPrice) {
        String oldValue = String.format("price=%.2f, specialPrice=%.2f", oldPrice, oldSpecialPrice);
        String newValue = String.format("price=%.2f, specialPrice=%.2f", newPrice, newSpecialPrice);
        String details = String.format("Admin %s changed price for product %d from %s to %s",
                adminUsername, productId, oldValue, newValue);

        AdminAuditLog log = AdminAuditLog.builder()
                .adminUserId(adminUserId)
                .adminUsername(adminUsername)
                .action("PRODUCT_PRICE_CHANGE")
                .entityType("Product")
                .entityId(String.valueOf(productId))
                .oldValue(oldValue)
                .newValue(newValue)
                .details(details)
                .build();

        return adminAuditLogRepository.save(log);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog logRoleChange(Long adminUserId, String adminUsername, Long userId,
                                       String oldRoles, String newRoles) {
        String details = String.format("Admin %s changed roles for user %d from [%s] to [%s]",
                adminUsername, userId, oldRoles, newRoles);

        AdminAuditLog log = AdminAuditLog.builder()
                .adminUserId(adminUserId)
                .adminUsername(adminUsername)
                .action("USER_ROLE_CHANGE")
                .entityType("User")
                .entityId(String.valueOf(userId))
                .oldValue(oldRoles)
                .newValue(newRoles)
                .details(details)
                .build();

        return adminAuditLogRepository.save(log);
    }

    @Override
    public List<AdminAuditLog> getRecentLogs() {
        return adminAuditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @Override
    public List<AdminAuditLog> getLogsByAction(String action) {
        return adminAuditLogRepository.findByActionOrderByCreatedAtDesc(action);
    }

    @Override
    public List<AdminAuditLog> getLogsByEntity(String entityType, String entityId) {
        return adminAuditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }
}
