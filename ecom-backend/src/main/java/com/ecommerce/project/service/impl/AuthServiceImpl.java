package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.payload.UserDTO;
import com.ecommerce.project.payload.UserResponse;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.service.AuthService;
import com.ecommerce.project.service.TotpService;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Service
@Transactional
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final RoleRepository roleRepository;
    private final ModelMapper modelMapper;

    private final TotpService totpService;

    @Override
    public AuthenticationResult login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Verifică dacă userul are 2FA activat
        User user = userRepository.findByUserName(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isTwoFactorEnabled()) {
            String tempToken = jwtUtils.generateJwtToken(userDetails);
            return new AuthenticationResult(null, null, true, tempToken);
        }

        // Login normal fără 2FA
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
        String jwtToken = jwtUtils.generateJwtToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        UserInfoResponse response = new UserInfoResponse(
                userDetails.getId(), userDetails.getUsername(), roles, userDetails.getEmail(), jwtToken
        );
        return new AuthenticationResult(response, jwtCookie, false, null);
    }

    @Override
    public ResponseEntity<MessageResponse> register(SignupRequest signupRequest) {


        if (userRepository.existsByUserName(signupRequest.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }
        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));

        }
        User user = new User(
                signupRequest.getUsername(),
                signupRequest.getEmail(),
                encoder.encode(signupRequest.getPassword())
        );
        user.setPasswordHint(signupRequest.getPasswordHint());
        Set<String> strRoles = signupRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found"));
            roles.add(userRole);
        } else {
            // admin --> ROLE_ADMIN
            // seller --> ROLE_SELLER

            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByRoleName(AppRole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found"));
                        roles.add(adminRole);
                        break;
                    case "seller":
                        Role sellerRole = roleRepository.findByRoleName(AppRole.ROLE_SELLER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found"));
                        roles.add(sellerRole);
                        break;
                    default:
                        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found"));
                        roles.add(userRole);
                }
            });
        }
        user.setRoles(roles);
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    // ACEASTA ESTE METODA MODIFICATĂ - Acum suportă și OAuth2 (GitHub)
    @Override
    public UserInfoResponse getCurrentUserDetails(Authentication authentication) {
        // Verificare dacă authentication e null
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();

        // Cazul 1: Login normal cu username/password (JWT)
        if (principal instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) principal;
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            return new UserInfoResponse(
                    userDetails.getId(),
                    userDetails.getUsername(),
                    roles,
                    userDetails.getEmail(),
                    null
            );
        }

        // Cazul 2: Login cu OAuth2 (GitHub, Google, etc.)
        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;

            // Extrage email-ul din GitHub
            String email = oauth2User.getAttribute("email");
            String name = oauth2User.getAttribute("name");

            if (email == null) {
                // Dacă GitHub nu returnează email, încercați să-l obțineți din altă parte
                throw new RuntimeException("Email not provided by GitHub. Please make sure your GitHub account has a public email.");
            }

            // Găsiți user-ul în baza de date
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

            // Obțineți rolurile
            List<String> roles = user.getRoles().stream()
                    .map(role -> role.getRoleName().name())
                    .collect(Collectors.toList());

            return new UserInfoResponse(
                    user.getUserId(),
                    user.getUserName(),
                    roles,
                    user.getEmail(),
                    null
            );
        }

        // Dacă niciun tip nu e recunoscut
        throw new RuntimeException("Unsupported authentication type: " + principal.getClass().getName());
    }

    @Override
    public ResponseCookie logoutUser() {
        return jwtUtils.getCleanJwtCookie();
    }

    @Override
    public UserResponse getAllSellers(Pageable pageable) {
        Page<User> allUsers = userRepository.findByRoleName(AppRole.ROLE_SELLER,pageable);
        List<UserDTO> userDTOs = allUsers.getContent()
                .stream()
                .map(p-> modelMapper.map(p,UserDTO.class))
                .collect(Collectors.toList());
        UserResponse response = new UserResponse();
        response.setContent(userDTOs);
        response.setPageNumber(allUsers.getNumber());
        response.setTotalElements(allUsers.getTotalElements());
        response.setTotalPages(allUsers.getTotalPages());
        response.setLastPage(allUsers.isLast());
        return response;
    }

    @Override
    public ResponseEntity<?> getPasswordHint(String username) {
        Optional<User> userOpt = userRepository.findByUserName(username);
        if(userOpt.isEmpty()){
            return ResponseEntity.badRequest().body(new MessageResponse("User not found"));
        }
        String hint = userOpt.get().getPasswordHint();
        if(hint == null || hint.isEmpty()){
            return ResponseEntity.ok(new MessageResponse("No hint available"));
        }
        return ResponseEntity.ok(new MessageResponse(hint));
    }

    @Override
    public GoogleAuthenticatorKey generate2FASecret(Long userId){
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new RuntimeException("User not found"));
        GoogleAuthenticatorKey key = totpService.generateSecret();
        user.setTwoFactorSecret(key.getKey());
        userRepository.save(user);
        return key;
    }

    @Override
    public boolean validate2FACode(Long userId, int code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return totpService.verifyCode(user.getTwoFactorSecret(), code);
    }

    @Override
    public void enable2FA(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    @Override
    public void disable2FA(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
    }

    @Override
    public boolean verify2FALogin(String jwtToken, int code) {
        String username = jwtUtils.getUserNameFromJWTToken(jwtToken);
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return validate2FACode(user.getUserId(), code);
    }



}
