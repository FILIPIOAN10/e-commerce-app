package com.ecommerce.project.controller;


import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.repository.UserRepository;
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
import org.apache.coyote.Response;
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
import com.ecommerce.project.security.request.ForgotPasswordRequest;

import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
// Controller pentru autentificare, înregistrare și gestionarea JWT
public class AuthController {

    private AuthService authService;
    private AuthUtil authUtil;

    private TotpService totpService;
    private JwtUtils jwtUtils;
    private UserRepository userRepository;

    public AuthController(AuthService authService,AuthUtil authUtil,TotpService totpService,UserRepository userRepository,JwtUtils jwtUtils) {

        this.authService=authService;
        this.authUtil = authUtil;
        this.totpService=totpService;
        this.userRepository=userRepository;
        this.jwtUtils=jwtUtils;
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
    public ResponseEntity<UserInfoResponse> getUserDetails(Authentication authentication) {

        return ResponseEntity.ok().
                body(authService.getCurrentUserDetails(authentication));
    }
    /**
     * Șterge cookie-ul JWT, efectiv deconectând utilizatorul.
     */
    @Tag(name = "Authentication")
    @PostMapping("/signout")
    public ResponseEntity<?> signoutUser() {
        ResponseCookie cookie = authService.logoutUser();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new MessageResponse("Successfully logged out!"));
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
        boolean isValid = authService.verify2FALogin(jwtToken, code);
        if (isValid) {
            String username = jwtUtils.getUserNameFromJWTToken(jwtToken);

            User user = userRepository.findByUserName(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<String> roles = user.getRoles().stream()
                    .map(role -> role.getRoleName().name())
                    .collect(Collectors.toList());

            String newToken = jwtUtils.generateTokenFromUsername(username);

            UserInfoResponse response = new UserInfoResponse(
                    user.getUserId(),
                    user.getUserName(),
                    roles,
                    user.getEmail(),
                    newToken
            );

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid 2FA Code");
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
}
