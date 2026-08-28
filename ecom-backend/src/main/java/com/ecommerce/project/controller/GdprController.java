package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ApiResponse;
import com.ecommerce.project.payload.GdprErasureRequest;
import com.ecommerce.project.service.gdpr.GdprService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service GDPR endpoints. Both act on the caller's own account only —
 * there is no user id in any path, so there is no object to reference
 * incorrectly.
 */
@RestController
@RequestMapping("/api/users/gdpr")
@RequiredArgsConstructor
@Tag(name = "GDPR")
public class GdprController {

    private final GdprService gdprService;
    private final AuthUtil authUtil;

    @Operation(summary = "Request a copy of your personal data (Art. 15)",
            description = "Queues the export; a single-use download link arrives by email.")
    @PostMapping("/export")
    public ResponseEntity<ApiResponse> requestExport() {
        return ResponseEntity.accepted()
                .body(new ApiResponse(gdprService.requestExport(authUtil.loggedInUser()), true));
    }

    @Operation(summary = "Begin deleting your account (Art. 17)",
            description = "Verifies your password and emails a confirmation link. Deletes nothing on its own.")
    @PostMapping("/erase")
    public ResponseEntity<ApiResponse> requestErasure(@Valid @RequestBody GdprErasureRequest request) {
        return ResponseEntity.accepted().body(new ApiResponse(
                gdprService.requestErasure(authUtil.loggedInUser(), request.getPassword()), true));
    }
}
