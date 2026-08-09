package com.ecommerce.project.controller;


import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.service.AuthService;
import com.ecommerce.project.service.TotpService;
import com.ecommerce.project.util.AuthUtil;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ecommerce.project.security.request.ForgotPasswordRequest;
import com.ecommerce.project.security.request.ResetPasswordRequest;
import com.ecommerce.project.security.request.UpdateProfileRequest;
import com.ecommerce.project.security.request.ChangePasswordRequest;
import com.ecommerce.project.security.redis.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;

import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
// Controller pentru autentificare, înregistrare și gestionarea JWT
public class AuthController {

    private AuthService authService;
    private AuthUtil authUtil;

    private TotpService totpService;
    private JwtUtils jwtUtils;
    private TokenBlacklistService tokenBlacklistService;

    public AuthController(AuthService authService,AuthUtil authUtil,TotpService totpService,JwtUtils jwtUtils,TokenBlacklistService tokenBlacklistService) {

        this.authService=authService;
        this.authUtil = authUtil;
        this.totpService=totpService;
        this.jwtUtils=jwtUtils;
        this.tokenBlacklistService=tokenBlacklistService;
    }


    // verifică user + parolă și generează JWT în cookie

    /**
     * Autentifică un utilizator pe baza username + password.
     * Dacă credentialele sunt valide, generează un JWT și îl trimite în cookie.
     */
    @Tag(name = "Authentication")
    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        try {
            AuthenticationResult result = authService.login(loginRequest);

            if (result.isNeeds2FA()) {
                Map<String, Object> body = new HashMap<>();
                body.put("needs2FA", true);
                body.put("temp2FAToken", result.getTemp2FAToken());
                return ResponseEntity.ok(body);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, result.getJwtCookie().toString())
                    .body(result.getResponse());
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Account locked")) {
                return ResponseEntity.status(429).body(Map.of("message", e.getMessage()));
            }
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }


    //creează user nou cu roluri

    /**
     * Înregistrează un utilizator nou și îi asociază rolurile corespunzătoare.
     */
    @Tag(name = "Authentication")
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        return authService.register(signupRequest);
    }

    //întoarce username-ul autentificat

    /**
     * Returnează numele utilizatorului autentificat.
     */
    @Tag(name = "Authentication")
    @GetMapping("/username")
    public String currentUsername(Authentication authentication) {
        if(authentication !=null ) {
            return authentication.getName();
        }
        else {
            return "";
        }
    }

    //returnează detalii user (id, username, roluri)
    // șterge cookie-ul JWT

    /**
     * Returnează informațiile complete ale utilizatorului autentificat (id, username, roluri).
     */
    @Tag(name = "Authentication")
    @GetMapping("/user")
    public ResponseEntity<?> getUserDetails(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        return ResponseEntity.ok().
                body(authService.getCurrentUserDetails(authentication));
    }
    /**
     * Șterge cookie-ul JWT, efectiv deconectând utilizatorul.
     */
    @Tag(name = "Authentication")
    @PostMapping("/signout")
    public ResponseEntity<?> signoutUser(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromHeader(request);
        if (jwt == null) {
            jwt = jwtUtils.getJwtFromCookies(request);
        }
        if (jwt != null) {
            try {
                Claims claims = jwtUtils.parseClaims(jwt);
                tokenBlacklistService.blacklistToken(jwt, claims);
            } catch (ExpiredJwtException e) {
                // Token already expired, no need to blacklist
            }
        }
        ResponseCookie cookie = authService.logoutUser();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new MessageResponse("Successfully logged out! Token revoked."));
    }

    @GetMapping("/sellers")
    public ResponseEntity<?> getAllSellers(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber
    )
    {
        Sort sortByAndOrder = Sort.by(AppConstants.SORT_USERS_BY).descending();
        Pageable pageDetails =  PageRequest.of(pageNumber,Integer.parseInt(AppConstants.PAGE_SIZE),sortByAndOrder);
        return ResponseEntity.ok(authService.getAllSellers((org.springframework.data.domain.Pageable) pageDetails));

    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20", required = false) Integer pageSize
    ) {
        Sort sortByAndOrder = Sort.by(AppConstants.SORT_USERS_BY).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        return ResponseEntity.ok(authService.getAllUsers(pageDetails));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        try {
            Long currentUserId = authUtil.loggedInUserId();
            if (userId.equals(currentUserId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "You cannot delete your own account"));
            }
            authService.deleteUser(userId);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/hint/{username}")
    public ResponseEntity<?> getPasswordHint(@PathVariable String username){
        return authService.getPasswordHint(username);
    }

    @PostMapping("/enable-2fa")
    public ResponseEntity<String> enable2FA() {
        Long userId = authUtil.loggedInUserId();
        GoogleAuthenticatorKey secret = authService.generate2FASecret(userId);
        String qrCodeUrl = totpService.getQrCodeUrl(secret, authUtil.loggedInEmail());
        return ResponseEntity.ok(qrCodeUrl);
    }

    @PostMapping("/disable-2fa")
    public ResponseEntity<String> disable2FA() {
        Long userId = authUtil.loggedInUserId();
        authService.disable2FA(userId);
        return ResponseEntity.ok("2FA disabled successfully");
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<String> verify2FA(@RequestParam int code) {
        Long userId = authUtil.loggedInUserId();
        boolean isValid = authService.validate2FACode(userId, code);
        if (!isValid) {
            return ResponseEntity.badRequest().body("Invalid 2FA code");
        }
        authService.enable2FA(userId);
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
            UserInfoResponse response = authService.complete2FALogin(jwtToken, code);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }
    @Tag(name = "Authentication")
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.initiatePasswordReset(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "If the email exists, a reset link has been sent"));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("message", "If the email exists, a reset link has been sent"));
        }
    }

    @Tag(name = "Authentication")
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }
    @Tag(name = "Authentication")
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }

    @Tag(name = "Authentication")
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.resendVerificationEmail(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "If the email exists and is not verified, a verification link has been sent"));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("message", "If the email exists and is not verified, a verification link has been sent"));
        }
    }

    @Tag(name = "Profile")
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                           Authentication authentication) {
        try {
            UserInfoResponse response = authService.updateProfile(request, authentication);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Tag(name = "Profile")
    @PostMapping("/profile/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            Authentication authentication) {
        try {
            authService.changePassword(request, authentication);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @Tag(name = "Profile")
    @PostMapping(value = "/profile/avatar", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                          Authentication authentication) {
        try {
            String avatarUrl = authService.uploadAvatar(file, authentication);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
