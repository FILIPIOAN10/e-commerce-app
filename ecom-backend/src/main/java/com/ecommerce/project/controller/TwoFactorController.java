package com.ecommerce.project.controller;

import com.ecommerce.project.model.User;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.service.TotpService;
import com.ecommerce.project.service.TwoFactorService;
import com.ecommerce.project.util.AuthUtil;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;
    private final AuthUtil authUtil;
    private final TotpService totpService;

    public TwoFactorController(TwoFactorService twoFactorService, AuthUtil authUtil, TotpService totpService) {
        this.twoFactorService = twoFactorService;
        this.authUtil = authUtil;
        this.totpService = totpService;
    }

    @PostMapping("/enable-2fa")
    public ResponseEntity<String> enable2FA() {
        Long userId = authUtil.loggedInUserId();
        GoogleAuthenticatorKey secret = twoFactorService.generate2FASecret(userId);
        String qrCodeUrl = totpService.getQrCodeUrl(secret, authUtil.loggedInEmail());
        return ResponseEntity.ok(qrCodeUrl);
    }

    @PostMapping("/disable-2fa")
    public ResponseEntity<String> disable2FA() {
        Long userId = authUtil.loggedInUserId();
        twoFactorService.disable2FA(userId);
        return ResponseEntity.ok("2FA disabled successfully");
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<String> verify2FA(@RequestParam int code) {
        Long userId = authUtil.loggedInUserId();
        boolean isValid = twoFactorService.validate2FACode(userId, code);
        if (!isValid) {
            return ResponseEntity.badRequest().body("Invalid 2FA code");
        }
        twoFactorService.enable2FA(userId);
        return ResponseEntity.ok("2FA enabled successfully");
    }

    @PostMapping("/user/2fa-status")
    public ResponseEntity<?> get2FAStatus() {
        User user = authUtil.loggedInUser();
        if (user != null) {
            return ResponseEntity.ok().body(Map.of("is2faEnabled", user.isTwoFactorEnabled()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }
    }

    @PostMapping("/public/verify-2fa-login")
    public ResponseEntity<?> verify2FALogin(@RequestBody Map<String, Object> requestBody) {
        int code = (Integer) requestBody.get("code");
        String jwtToken = (String) requestBody.get("jwtToken");
        try {
            UserInfoResponse response = twoFactorService.complete2FALogin(jwtToken, code);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
