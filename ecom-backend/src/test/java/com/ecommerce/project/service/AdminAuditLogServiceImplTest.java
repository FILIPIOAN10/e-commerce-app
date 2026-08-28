package com.ecommerce.project.service;

import com.ecommerce.project.model.AdminAuditLog;
import com.ecommerce.project.repository.AdminAuditLogRepository;
import com.ecommerce.project.service.impl.AdminAuditLogServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceImplTest {

    @Mock private AdminAuditLogRepository adminAuditLogRepository;

    @InjectMocks
    private AdminAuditLogServiceImpl adminAuditLogService;

    @Test
    void logPriceChange_savesAuditLogWithCorrectValues() {
        when(adminAuditLogRepository.save(any(AdminAuditLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adminAuditLogService.logPriceChange(1L, "admin", 5L, new BigDecimal("100.00"),
                new BigDecimal("120.00"), new BigDecimal("90.00"), new BigDecimal("108.00"));

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(captor.capture());

        AdminAuditLog log = captor.getValue();
        assertEquals(1L, log.getAdminUserId());
        assertEquals("admin", log.getAdminUsername());
        assertEquals("PRODUCT_PRICE_CHANGE", log.getAction());
        assertEquals("Product", log.getEntityType());
        assertEquals("5", log.getEntityId());
        assertTrue(log.getOldValue().contains("100.0"));
        assertTrue(log.getNewValue().contains("120.0"));
        assertNotNull(log.getDetails());
    }

    @Test
    void logRoleChange_savesAuditLogWithCorrectValues() {
        when(adminAuditLogRepository.save(any(AdminAuditLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adminAuditLogService.logRoleChange(1L, "admin", 10L, "ROLE_USER", "ROLE_SELLER");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(captor.capture());

        AdminAuditLog log = captor.getValue();
        assertEquals("USER_ROLE_CHANGE", log.getAction());
        assertEquals("User", log.getEntityType());
        assertEquals("10", log.getEntityId());
        assertEquals("ROLE_USER", log.getOldValue());
        assertEquals("ROLE_SELLER", log.getNewValue());
    }

    @Test
    void getRecentLogs_delegatesToRepository() {
        when(adminAuditLogRepository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of());
        assertTrue(adminAuditLogService.getRecentLogs().isEmpty());
    }

    @Test
    void getLogsByAction_delegatesToRepository() {
        when(adminAuditLogRepository.findByActionOrderByCreatedAtDesc("PRODUCT_PRICE_CHANGE"))
                .thenReturn(List.of());
        assertTrue(adminAuditLogService.getLogsByAction("PRODUCT_PRICE_CHANGE").isEmpty());
    }

    @Test
    void getLogsByEntity_delegatesToRepository() {
        when(adminAuditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("Product", "5"))
                .thenReturn(List.of());
        assertTrue(adminAuditLogService.getLogsByEntity("Product", "5").isEmpty());
    }
}
