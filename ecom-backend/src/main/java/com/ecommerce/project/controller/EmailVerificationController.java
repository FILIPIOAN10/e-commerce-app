package com.ecommerce.project.controller;

import com.ecommerce.project.security.request.ForgotPasswordRequest;
import com.ecommerce.project.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @Tag(name = "Authentication")
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            emailVerificationService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }

    @Tag(name = "Authentication")
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            emailVerificationService.resendVerificationEmail(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "If the email exists and is not verified, a verification link has been sent"));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("message", "If the email exists and is not verified, a verification link has been sent"));
        }
    }
}
