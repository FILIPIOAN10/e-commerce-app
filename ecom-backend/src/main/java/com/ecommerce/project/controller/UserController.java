package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.service.UserManagementService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserManagementService userManagementService;
    private final AuthUtil authUtil;

    public UserController(UserManagementService userManagementService, AuthUtil authUtil) {
        this.userManagementService = userManagementService;
        this.authUtil = authUtil;
    }

    @GetMapping("/sellers")
    public ResponseEntity<?> getAllSellers(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber
    ) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, Integer.parseInt(AppConstants.PAGE_SIZE), AppConstants.SORT_USERS_BY, "desc");
        return ResponseEntity.ok(userManagementService.getAllSellers(pageDetails));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20", required = false) Integer pageSize
    ) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, AppConstants.SORT_USERS_BY, "desc");
        return ResponseEntity.ok(userManagementService.getAllUsers(pageDetails));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        try {
            Long currentUserId = authUtil.loggedInUserId();
            if (userId.equals(currentUserId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "You cannot delete your own account"));
            }
            userManagementService.deleteUser(userId);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/hint/{username}")
    public ResponseEntity<?> getPasswordHint(@PathVariable String username){
        return userManagementService.getPasswordHint(username);
    }
}
