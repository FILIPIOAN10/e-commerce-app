package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.AdminAuditLog;
import com.ecommerce.project.payload.UserActivityLogResponse;
import com.ecommerce.project.service.AdminAuditLogService;
import com.ecommerce.project.service.UserActivityLogService;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.SortWhitelist;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The read-only admin log views: what an administrator did (audit log) and what
 * users did (activity log). Both are admin-only and both are queries, so they
 * share a controller rather than a class each.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminAuditLogService adminAuditLogService;
    private final UserActivityLogService userActivityLogService;

    @Tag(name = "Admin Audit Logs")
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminAuditLog>> getRecentLogs() {
        return ResponseEntity.ok(adminAuditLogService.getRecentLogs());
    }

    @Tag(name = "Admin Audit Logs")
    @GetMapping("/audit-logs/action/{action}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminAuditLog>> getLogsByAction(@PathVariable String action) {
        return ResponseEntity.ok(adminAuditLogService.getLogsByAction(action));
    }

    @Tag(name = "Admin Audit Logs")
    @GetMapping("/audit-logs/entity/{entityType}/{entityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminAuditLog>> getLogsByEntity(@PathVariable String entityType,
                                                               @PathVariable String entityId) {
        return ResponseEntity.ok(adminAuditLogService.getLogsByEntity(entityType, entityId));
    }

    @Tag(name = "User Activity Log")
    @GetMapping("/activity-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserActivityLogResponse> getActivityLogs(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, "createdAt", "desc",
                "createdAt", SortWhitelist.ACTIVITY_LOG);
        return new ResponseEntity<>(userActivityLogService.getLogs(pageDetails), HttpStatus.OK);
    }
}
