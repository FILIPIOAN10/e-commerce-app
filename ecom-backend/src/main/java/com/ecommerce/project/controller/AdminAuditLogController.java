package com.ecommerce.project.controller;

import com.ecommerce.project.model.AdminAuditLog;
import com.ecommerce.project.service.AdminAuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @Tag(name = "Admin Audit Logs")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminAuditLog>> getRecentLogs() {
        return ResponseEntity.ok(adminAuditLogService.getRecentLogs());
    }

    @Tag(name = "Admin Audit Logs")
    @GetMapping("/action/{action}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminAuditLog>> getLogsByAction(@PathVariable String action) {
        return ResponseEntity.ok(adminAuditLogService.getLogsByAction(action));
    }

    @Tag(name = "Admin Audit Logs")
    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminAuditLog>> getLogsByEntity(@PathVariable String entityType,
                                                               @PathVariable String entityId) {
        return ResponseEntity.ok(adminAuditLogService.getLogsByEntity(entityType, entityId));
    }
}
