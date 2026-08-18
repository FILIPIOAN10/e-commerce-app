package com.ecommerce.project.controller;


import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.service.AuthService;
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

    public AuthController(AuthService authService, JwtUtils jwtUtils, TokenBlacklistService tokenBlacklistService) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
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
}
