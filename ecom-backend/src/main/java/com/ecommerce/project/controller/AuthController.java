package com.ecommerce.project.controller;


import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.redis.RefreshTokenService;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.service.AuthService;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.security.redis.LoginAttemptService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.ecommerce.project.security.redis.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;


import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
// Controller pentru autentificare, înregistrare și gestionarea JWT
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;

    public AuthController(AuthService authService, JwtUtils jwtUtils, TokenBlacklistService tokenBlacklistService,
                          RefreshTokenService refreshTokenService, LoginAttemptService loginAttemptService,
                          EmailService emailService) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.emailService = emailService;
    }


    // verifică user + parolă și generează JWT în cookie

    /**
     * Autentifică un utilizator pe baza username + password.
     * Dacă credentialele sunt valide, generează un JWT și îl trimite în cookie.
     */
    @Tag(name = "Authentication")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated, or a 2FA challenge is required"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "429", description = "Account locked after too many failed attempts",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        AuthenticationResult result = authService.login(loginRequest);

        if (result.isNeeds2FA()) {
            Map<String, Object> body = new HashMap<>();
            body.put("needs2FA", true);
            body.put("temp2FAToken", result.getTemp2FAToken());
            return ResponseEntity.ok(body);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.getJwtCookie().toString())
                .header(HttpHeaders.SET_COOKIE, result.getRefreshCookie().toString())
                .body(result.getResponse());
    }


    //creează user nou cu roluri

    /**
     * Înregistrează un utilizator nou și îi asociază rolurile corespunzătoare.
     */
    @Tag(name = "Authentication")
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        return ResponseEntity.ok(authService.register(signupRequest));
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

    /**
     * Șterge cookie-ul JWT, efectiv deconectând utilizatorul.
     */
    @Tag(name = "Authentication")
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(HttpServletRequest request) {
        String refreshToken = jwtUtils.getRefreshTokenFromCookies(request);
        if (refreshToken == null) {
            refreshToken = refreshTokenService.getRefreshTokenFromCookies(request);
        }
        AuthenticationResult result = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.getJwtCookie().toString())
                .header(HttpHeaders.SET_COOKIE, result.getRefreshCookie().toString())
                .body(result.getResponse());
    }

    @Tag(name = "Authentication")
    @GetMapping("/devices")
    public ResponseEntity<?> getActiveSessions(Authentication authentication, HttpServletRequest request) {
        String username = authentication.getName();
        String currentToken = refreshTokenService.getRefreshTokenFromCookies(request);
        return ResponseEntity.ok(refreshTokenService.getSessions(username, currentToken));
    }

    @Tag(name = "Authentication")
    @DeleteMapping("/devices/{token}")
    public ResponseEntity<?> revokeSession(@PathVariable String token, Authentication authentication) {
        refreshTokenService.revokeSession(authentication.getName(), token);
        return ResponseEntity.ok(new MessageResponse("Device session revoked successfully"));
    }

    @Tag(name = "Authentication")
    @DeleteMapping("/devices")
    public ResponseEntity<?> revokeAllOtherSessions(Authentication authentication, HttpServletRequest request) {
        String username = authentication.getName();
        String currentToken = refreshTokenService.getRefreshTokenFromCookies(request);
        refreshTokenService.revokeAllOtherSessions(username, currentToken);
        return ResponseEntity.ok(new MessageResponse("All other devices have been signed out"));
    }

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
        String refreshToken = jwtUtils.getRefreshTokenFromCookies(request);
        if (refreshToken == null) {
            refreshToken = refreshTokenService.getRefreshTokenFromCookies(request);
        }
        refreshTokenService.delete(refreshToken);

        ResponseCookie accessCookie = authService.logoutUser();
        ResponseCookie refreshCookie = refreshTokenService.getCleanRefreshCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new MessageResponse("Successfully logged out! Token revoked."));
    }

    /**
     * Permite unui utilizator cu contul blocat să solicite deblocarea de către admin.
     * Trimite un email de notificare adminului; nu deblochează automat contul.
     */
    @Tag(name = "Authentication")
    @PostMapping("/unlock-request")
    public ResponseEntity<?> requestUnlock(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username is required"));
        }
        if (!loginAttemptService.isLocked(username)) {
            return ResponseEntity.ok(new MessageResponse("This account is not currently locked."));
        }
        emailService.sendUnlockRequestEmail(username);
        return ResponseEntity.ok(new MessageResponse("Your unlock request has been sent to the admin."));
    }
}
