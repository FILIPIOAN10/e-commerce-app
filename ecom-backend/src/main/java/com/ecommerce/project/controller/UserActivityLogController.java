package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.UserActivityLogResponse;
import com.ecommerce.project.service.UserActivityLogService;
import com.ecommerce.project.util.PaginationUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserActivityLogController {

    private final UserActivityLogService userActivityLogService;

    @Tag(name = "User Activity Log")
    @GetMapping("/admin/activity-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserActivityLogResponse> getActivityLogs(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, "createdAt", "desc");
        return new ResponseEntity<>(userActivityLogService.getLogs(pageDetails), HttpStatus.OK);
    }
}
