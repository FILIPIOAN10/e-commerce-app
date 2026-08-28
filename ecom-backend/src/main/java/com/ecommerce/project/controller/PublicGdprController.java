package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ApiResponse;
import com.ecommerce.project.service.gdpr.GdprArchive;
import com.ecommerce.project.service.gdpr.GdprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two GDPR endpoints reached from an emailed link, where the recipient is
 * not necessarily signed in. Neither trusts the caller: each is gated on a
 * signed, single-use, purpose-scoped token, and neither takes a user id.
 */
@RestController
@RequestMapping("/api/public/gdpr")
@RequiredArgsConstructor
@Tag(name = "GDPR")
public class PublicGdprController {

    private final GdprService gdprService;

    @Operation(summary = "Download a prepared data export",
            description = "Exchanges a single-use link token for the ZIP archive.")
    @GetMapping("/export/download")
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
    @PostMapping("/erase/confirm")
    public ResponseEntity<ApiResponse> confirmErasure(@RequestParam("token") String token) {
        return ResponseEntity.ok(new ApiResponse(gdprService.confirmErasure(token), true));
    }
}
