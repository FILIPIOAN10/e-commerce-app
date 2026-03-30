package com.ecommerce.project.controller;


import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Pageable;
@RestController
@RequestMapping("/api/auth")
// Controller pentru autentificare, înregistrare și gestionarea JWT
public class AuthController {

    private AuthService authService;


    public AuthController(AuthService authService) {

        this.authService=authService;
    }


    // verifică user + parolă și generează JWT în cookie

    /**
     * Autentifică un utilizator pe baza username + password.
     * Dacă credentialele sunt valide, generează un JWT și îl trimite în cookie.
     */
    @Tag(name = "Authentication")
    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {

         AuthenticationResult result = authService.login(loginRequest);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, result.getJwtCookie().toString()).body(result.getResponse());
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

    @GetMapping("/hint/{username}")
    public ResponseEntity<?> getPasswordHint(@PathVariable String username){
        return authService.getPasswordHint(username);
    }
}
