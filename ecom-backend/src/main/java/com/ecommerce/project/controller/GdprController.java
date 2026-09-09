package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ApiResponse;
import com.ecommerce.project.payload.GdprErasureRequest;
import com.ecommerce.project.service.gdpr.GdprArchive;
import com.ecommerce.project.service.gdpr.GdprService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every GDPR endpoint, in the two halves the subject actually experiences.
 *
 * <p><b>/api/users/gdpr/**</b> — self-service, called by the signed-in account
 * holder. Both act on the caller's own account only: there is no user id in any
 * path, so there is no object to reference incorrectly.
 *
 * <p><b>/api/public/gdpr/**</b> — reached from an emailed link, where the
 * recipient is not necessarily signed in. Neither trusts the caller: each is
 * gated on a signed, single-use, purpose-scoped token, and neither takes a user
 * id.
 *
 * <p>The class maps {@code /api} and each method carries its own full path,
 * because the {@code /users/} and {@code /public/} prefixes are what the security
 * config matches on — authenticated for the first, permitAll for the second.
 * Do not shorten these paths.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "GDPR")
public class GdprController {

    private final GdprService gdprService;
    private final AuthUtil authUtil;

    @Operation(summary = "Request a copy of your personal data (Art. 15)",
            description = "Queues the export; a single-use download link arrives by email.")
    @PostMapping("/users/gdpr/export")
    public ResponseEntity<ApiResponse> requestExport() {
        return ResponseEntity.accepted()
                .body(new ApiResponse(gdprService.requestExport(authUtil.loggedInUser()), true));
    }

    @Operation(summary = "Begin deleting your account (Art. 17)",
            description = "Verifies your password and emails a confirmation link. Deletes nothing on its own.")
    @PostMapping("/users/gdpr/erase")
    public ResponseEntity<ApiResponse> requestErasure(@Valid @RequestBody GdprErasureRequest request) {
        return ResponseEntity.accepted().body(new ApiResponse(
                gdprService.requestErasure(authUtil.loggedInUser(), request.getPassword()), true));
    }

    @Operation(summary = "Download a prepared data export",
            description = "Exchanges a single-use link token for the ZIP archive.")
    @GetMapping("/public/gdpr/export/download")
    public ResponseEntity<Resource> download(@RequestParam("token") String token) {
        GdprArchive archive = gdprService.downloadExport(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archive.fileName() + "\"")
                // A copy of someone's personal data must not sit in a proxy cache.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(archive.content().length)
                .body(new ByteArrayResource(archive.content()));
    }

    @Operation(summary = "Confirm account deletion",
            description = "Spends the emailed confirmation token and performs the erasure. Irreversible.")
    @PostMapping("/public/gdpr/erase/confirm")
    public ResponseEntity<ApiResponse> confirmErasure(@RequestParam("token") String token) {
        return ResponseEntity.ok(new ApiResponse(gdprService.confirmErasure(token), true));
    }
}
