package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.AdminAuditLog;
import com.ecommerce.project.repository.AdminAuditLogRepository;
import com.ecommerce.project.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog logPriceChange(Long adminUserId, String adminUsername, Long productId,
                                        BigDecimal oldPrice, BigDecimal newPrice,
                                        BigDecimal oldSpecialPrice, BigDecimal newSpecialPrice) {
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog logAccountUnlock(Long adminUserId, String adminUsername, Long userId, String targetUsername) {
        String details = String.format("Admin %s unlocked account for user %s (id=%d)",
                adminUsername, targetUsername, userId);

        AdminAuditLog log = AdminAuditLog.builder()
                .adminUserId(adminUserId)
                .adminUsername(adminUsername)
                .action("USER_ACCOUNT_UNLOCK")
                .entityType("User")
                .entityId(String.valueOf(userId))
                .oldValue("LOCKED")
                .newValue("UNLOCKED")
                .details(details)
                .build();

        return adminAuditLogRepository.save(log);
    }

    /**
     * The one audit entry written in the caller's transaction rather than a new
     * one: if the erasure rolls back, the claim that it happened must roll back
     * with it.
     */
    @Override
    @Transactional
    public AdminAuditLog logGdprErasure(Long userId, String pseudonym) {
        AdminAuditLog log = AdminAuditLog.builder()
                .adminUserId(userId)
                .adminUsername(pseudonym)
                .action("GDPR_ERASURE")
                .entityType("User")
                .entityId(String.valueOf(userId))
                .oldValue("ACTIVE")
                .newValue("ERASED")
                .details("Account erased on the user's own request under GDPR Art. 17; "
                        + "retained fiscal records anonymised to " + pseudonym)
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
