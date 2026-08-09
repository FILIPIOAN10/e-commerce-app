package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.AccountLockedException;
import com.ecommerce.project.exception.AccountNotVerifiedException;
import com.ecommerce.project.exception.EmailAlreadyExistsException;
import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.exception.UserNotFoundException;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.payload.UserDTO;
import com.ecommerce.project.payload.UserResponse;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.redis.LoginAttemptService;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.request.UpdateProfileRequest;
import com.ecommerce.project.security.request.ChangePasswordRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.service.AuthService;
import com.ecommerce.project.service.TotpService;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ecommerce.project.security.redis.PasswordResetService;
import com.ecommerce.project.service.EmailService;
import io.jsonwebtoken.Claims;
import com.ecommerce.project.security.redis.EmailVerificationService;
import com.ecommerce.project.util.AuthUtil;
import org.springframework.beans.factory.annotation.Value;

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
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;
    private final AuthUtil authUtil;

    @Value("${image.base.url}")
    private String imageBaseUrl;

    @Override
    public AuthenticationResult login(LoginRequest loginRequest) {
        String username = loginRequest.getUsername();

        if (loginAttemptService.isLocked(username)) {
            long minutesLeft = loginAttemptService.getLockTimeRemaining(username) / 60;
            throw new AccountLockedException("Account locked due to too many failed attempts. "
                    + "Try again in " + minutesLeft + " minutes.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            loginAttemptService.resetAttempts(username);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            User user = userRepository.findByUserName(loginRequest.getUsername())
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
            if (!user.isVerified()) {
                throw new AccountNotVerifiedException("Account not verified. Please check your email to verify your account.");
            }

            if (user.isTwoFactorEnabled()) {
                String tempToken = jwtUtils.generateTwoFactorToken(userDetails);
                return new AuthenticationResult(null, null, true, tempToken);
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);
            ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
            String jwtToken = jwtUtils.generateJwtToken(userDetails);

            UserInfoResponse response = buildUserInfoResponse(user, jwtToken);
            return new AuthenticationResult(response, jwtCookie, false, null);

        } catch (org.springframework.security.core.AuthenticationException e) {
            loginAttemptService.recordFailedAttempt(username);
            int remaining = loginAttemptService.getRemainingAttempts(username);
            if (remaining > 0) {
                throw new InvalidCredentialsException("Invalid credentials. " + remaining + " attempts remaining.");
            } else {
                throw new AccountLockedException("Account locked due to too many failed attempts. "
                        + "Try again in 15 minutes.");
            }
        }
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
        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found"));
        user.setRoles(Set.of(userRole));
        user.setVerified(false);
        userRepository.save(user);

        String token = emailVerificationService.generateVerificationToken(user.getEmail());
        emailService.sendVerificationEmail(user.getEmail(), token);

        return ResponseEntity.ok(new MessageResponse("User registered successfully! Please check your email to verify your account."));
    }

    @Override
    public UserInfoResponse getCurrentUserDetails(Authentication authentication) {
        User user = authUtil.loggedInUser();
        return buildUserInfoResponse(user, null);
    }

    private UserInfoResponse buildUserInfoResponse(User user, String jwtToken) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getRoleName().name())
                .collect(Collectors.toList());
        return new UserInfoResponse(
                user.getUserId(),
                user.getUserName(),
                roles,
                user.getEmail(),
                jwtToken,
                user.getPhone(),
                user.getAvatarUrl()
        );
    }

    @Override
    public ResponseCookie logoutUser() {
        return jwtUtils.getCleanJwtCookie();
    }

    @Override
    public UserResponse getAllSellers(Pageable pageable) {
        Page<User> allUsers = userRepository.findByRoleName(AppRole.ROLE_SELLER, pageable);
        List<UserDTO> userDTOs = allUsers.getContent()
                .stream()
                .map(p -> modelMapper.map(p, UserDTO.class))
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
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("User not found"));
        }
        String hint = userOpt.get().getPasswordHint();
        if (hint == null || hint.isEmpty()) {
            return ResponseEntity.ok(new MessageResponse("No hint available"));
        }
        return ResponseEntity.ok(new MessageResponse(hint));
    }

    @Override
    public GoogleAuthenticatorKey generate2FASecret(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
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
        if (!jwtUtils.validateTwoFactorToken(jwtToken)) {
            return false;
        }
        String username = jwtUtils.getUserNameFromJWTToken(jwtToken);
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return validate2FACode(user.getUserId(), code);
    }

    @Override
    public UserInfoResponse complete2FALogin(String jwtToken, int code) {
        if (!verify2FALogin(jwtToken, code)) {
            throw new InvalidCredentialsException("Invalid 2FA code");
        }
        String username = jwtUtils.getUserNameFromJWTToken(jwtToken);
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        String newToken = jwtUtils.generateTokenFromUsername(username);
        return buildUserInfoResponse(user, newToken);
    }
    @Override
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        String token = passwordResetService.generateResetToken(email);
        emailService.sendPasswordResetEmail(email, token);
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        String email = passwordResetService.extractEmailFromToken(token);

        if (!passwordResetService.validateResetToken(token, email)) {
            throw new RuntimeException("Invalid or expired reset token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        passwordResetService.invalidateToken(email);
    }
    @Override
    public void verifyEmail(String token) {
        String email = emailVerificationService.extractEmailFromToken(token);

        if (!emailVerificationService.validateVerificationToken(token, email)) {
            throw new RuntimeException("Invalid or expired verification token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setVerified(true);
        userRepository.save(user);

        emailVerificationService.invalidateToken(email);
    }

    @Override
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isVerified()) {
            throw new RuntimeException("Account is already verified");
        }

        String token = emailVerificationService.generateVerificationToken(email);
        emailService.sendVerificationEmail(email, token);
    }

    @Override
    public UserInfoResponse updateProfile(UpdateProfileRequest request, Authentication authentication) {
        User user = authUtil.loggedInUser();

        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (!request.getEmail().equals(user.getEmail())
                    && userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyExistsException("Email is already in use by another account");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        userRepository.save(user);

        return buildUserInfoResponse(user, null);
    }

    @Override
    public void changePassword(ChangePasswordRequest request, Authentication authentication) {
        User user = authUtil.loggedInUser();

        if (!encoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public UserResponse getAllUsers(Pageable pageable) {
        Page<User> allUsers = userRepository.findAll(pageable);
        List<UserDTO> userDTOs = allUsers.getContent()
                .stream()
                .map(p -> modelMapper.map(p, UserDTO.class))
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
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        userRepository.delete(user);
    }

    @Override
    public String uploadAvatar(MultipartFile file, Authentication authentication) {
        User user = authUtil.loggedInUser();

        try {
            String uploadDir = "images/avatars/";
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            }

            List<String> allowedExtensions = List.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
            if (!allowedExtensions.contains(fileExtension)) {
                throw new RuntimeException("Invalid file type. Allowed: jpg, jpeg, png, gif, webp");
            }

            String fileName = "avatar_" + user.getUserId() + "_" + System.currentTimeMillis() + fileExtension;
            java.nio.file.Path filePath = uploadPath.resolve(fileName);
            java.nio.file.Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = imageBaseUrl + "/avatars/" + fileName;
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            return avatarUrl;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload avatar: " + e.getMessage());
        }
    }

}
