package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ReturnRequestDTO;
import com.ecommerce.project.service.ReturnService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;
    private final AuthUtil authUtil;

    @Tag(name = "Returns")
    @PostMapping("/orders/{orderId}/return")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReturnRequestDTO> requestReturn(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {
        String email = authUtil.loggedInEmail();
        String reason = body.getOrDefault("reason", "No reason provided");
        ReturnRequestDTO dto = returnService.requestReturn(orderId, email, reason);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @Tag(name = "Returns")
    @GetMapping("/orders/my-returns")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ReturnRequestDTO>> getMyReturns(
            @RequestParam(name = "page", defaultValue = "0", required = false) int page,
            @RequestParam(name = "size", defaultValue = "10", required = false) int size) {
        String email = authUtil.loggedInEmail();
        return ResponseEntity.ok(returnService.getMyReturnRequests(email, page, size));
    }

    @Tag(name = "Returns")
    @GetMapping("/admin/returns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReturnRequestDTO>> getAllReturns(
            @RequestParam(name = "page", defaultValue = "0", required = false) int page,
            @RequestParam(name = "size", defaultValue = "10", required = false) int size) {
        return ResponseEntity.ok(returnService.getAllReturnRequests(page, size));
    }

    @Tag(name = "Returns")
    @PutMapping("/admin/returns/{returnId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReturnRequestDTO> approveReturn(
            @PathVariable Long returnId,
            @RequestBody(required = false) Map<String, String> body) {
        String adminNote = body != null ? body.getOrDefault("adminNote", "") : "";
        return ResponseEntity.ok(returnService.approveReturn(returnId, adminNote));
    }

    @Tag(name = "Returns")
    @PutMapping("/admin/returns/{returnId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReturnRequestDTO> rejectReturn(
            @PathVariable Long returnId,
            @RequestBody(required = false) Map<String, String> body) {
        String adminNote = body != null ? body.getOrDefault("adminNote", "") : "";
        return ResponseEntity.ok(returnService.rejectReturn(returnId, adminNote));
    }

    @Tag(name = "Returns")
    @PutMapping("/admin/returns/{returnId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReturnRequestDTO> markAsRefunded(@PathVariable Long returnId) {
        return ResponseEntity.ok(returnService.markAsRefunded(returnId));
    }
}
