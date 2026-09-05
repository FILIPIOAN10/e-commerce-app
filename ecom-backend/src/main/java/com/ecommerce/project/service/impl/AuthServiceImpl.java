package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.AccountLockedException;
import com.ecommerce.project.exception.AccountNotVerifiedException;
import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.exception.UserNotFoundException;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.redis.LoginAttemptService;
import com.ecommerce.project.security.redis.RefreshTokenService;
import com.ecommerce.project.security.services.UserDetailsServiceImpl;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.service.AuthService;
import com.ecommerce.project.exception.UsernameAlreadyExistsException;
import com.ecommerce.project.exception.EmailAlreadyExistsException;
import com.ecommerce.project.exception.RoleNotFoundException;
import com.ecommerce.project.exception.EmailDeliveryException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.security.redis.EmailVerificationService;
import com.ecommerce.project.util.UserInfoMapper;
import org.springframework.beans.factory.annotation.Value;

import java.util.Set;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final RoleRepository roleRepository;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${app.skip-verification-email:false}")
    private boolean skipVerificationEmail;

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
                return new AuthenticationResult(null, null, null, true, tempToken);
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);
            ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
            String refreshToken = refreshTokenService.createRefreshToken(user.getUserName());
            ResponseCookie refreshCookie = refreshTokenService.generateRefreshCookie(refreshToken);

            UserInfoResponse response = UserInfoMapper.toUserInfoResponse(user);
            return new AuthenticationResult(response, jwtCookie, refreshCookie, false, null);

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
    public MessageResponse register(SignupRequest signupRequest) {
        if (userRepository.existsByUserName(signupRequest.getUsername())) {
            throw new UsernameAlreadyExistsException("Error: Username is already taken!");
        }
        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new EmailAlreadyExistsException("Error: Email is already in use!");
        }
        User user = new User(
                signupRequest.getUsername(),
                signupRequest.getEmail(),
                encoder.encode(signupRequest.getPassword())
        );
        user.setPasswordHint(signupRequest.getPasswordHint());
        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                .orElseThrow(() -> new RoleNotFoundException("Error: Role is not found"));
        user.setRoles(Set.of(userRole));
        user.setVerified(false);
        userRepository.save(user);

        if (skipVerificationEmail) {
            user.setVerified(true);
            userRepository.save(user);
            return new MessageResponse("User registered successfully!");
        }

        String token = emailVerificationService.generateVerificationToken(user.getEmail());
        try {
            emailService.sendVerificationEmail(user.getEmail(), token);
        } catch (EmailDeliveryException e) {
            // The one place a delivery failure must not become the response.
            // This class is @Transactional, so letting it out would roll the
            // registration back and the account would silently not exist. An
            // unverified account is a state the domain already models and has a
            // resend endpoint for, so keep it and say what happened — better
            // than discarding a valid signup because SMTP hiccuped.
            log.error("Registration succeeded but the verification email failed for {}", user.getEmail(), e);
            return new MessageResponse("User registered successfully, but we could not send the "
                    + "verification email. Please use \"resend verification\" to try again.");
        }

        return new MessageResponse("User registered successfully! Please check your email to verify your account.");
    }

    @Override
    public ResponseCookie logoutUser() {
        return jwtUtils.getCleanJwtCookie();
    }

    @Override
    public AuthenticationResult refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException("Refresh token is required");
        }

        String newRefreshToken = refreshTokenService.rotate(refreshToken);
        String username = refreshTokenService.validateAndGetUsername(newRefreshToken);

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Erasure revokes every refresh token, so this should be unreachable —
        // but a token minted a moment before the erasure committed must not be
        // able to mint a fresh access cookie for a deleted account.
        if (user.isErased()) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(username);
        ResponseCookie accessCookie = jwtUtils.generateJwtCookie(userDetails);
        ResponseCookie refreshCookie = refreshTokenService.generateRefreshCookie(newRefreshToken);

        UserInfoResponse response = UserInfoMapper.toUserInfoResponse(user);
        return new AuthenticationResult(response, accessCookie, refreshCookie, false, null);
    }
}
