package com.ecommerce.project.controller;

import com.ecommerce.project.security.request.ForgotPasswordRequest;
import com.ecommerce.project.security.request.ResetPasswordRequest;
import com.ecommerce.project.service.EmailVerificationService;
import com.ecommerce.project.service.PasswordResetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The two flows that recover an account by email: proving you own the address
 * (verification) and getting back in without the password (reset). Both are
 * public, both are one-token-per-link, and both answer identically whether or
 * not the address exists, so neither can be used to enumerate users.
 */
@RestController
@RequestMapping("/api/auth")
public class AccountRecoveryController {

    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AccountRecoveryController(PasswordResetService passwordResetService,
                                     EmailVerificationService emailVerificationService) {
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @Tag(name = "Authentication")
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            passwordResetService.initiatePasswordReset(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "If the email exists, a reset link has been sent"));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("message", "If the email exists, a reset link has been sent"));
        }
    }

    @Tag(name = "Authentication")
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
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
