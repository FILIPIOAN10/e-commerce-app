package com.ecommerce.project.service;

import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.exception.UserNotFoundException;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.redis.RefreshTokenService;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.service.impl.TwoFactorServiceImpl;
import com.ecommerce.project.util.UserInfoMapper;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseCookie;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TwoFactorServiceImpl tests")
class TwoFactorServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TotpService totpService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private TwoFactorServiceImpl twoFactorService;

    private User user;
    private GoogleAuthenticatorKey key;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(1L);
        user.setUserName("user1");
        user.setEmail("user1@test.com");
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);

        key = new GoogleAuthenticatorKey.Builder("SECRETKEY").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("generate2FASecret stores the secret on the user")
    void generate2FASecret_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(totpService.generateSecret()).thenReturn(key);
        when(userRepository.save(any(User.class))).thenReturn(user);

        GoogleAuthenticatorKey result = twoFactorService.generate2FASecret(1L);

        assertNotNull(result);
        assertEquals("SECRETKEY", result.getKey());
        assertEquals("SECRETKEY", user.getTwoFactorSecret());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("validate2FACode returns true for valid TOTP code")
    void validate2FACode_valid_returnsTrue() {
        user.setTwoFactorSecret("SECRETKEY");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRETKEY", 123456)).thenReturn(true);

        boolean valid = twoFactorService.validate2FACode(1L, 123456);

        assertTrue(valid);
        verify(totpService).verifyCode("SECRETKEY", 123456);
    }

    @Test
    @DisplayName("validate2FACode returns false for expired/invalid TOTP code")
    void validate2FACode_invalid_returnsFalse() {
        user.setTwoFactorSecret("SECRETKEY");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRETKEY", 999999)).thenReturn(false);

        boolean valid = twoFactorService.validate2FACode(1L, 999999);

        assertFalse(valid);
    }

    @Test
    @DisplayName("enable2FA marks the user as two-factor enabled")
    void enable2FA_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        twoFactorService.enable2FA(1L);

        assertTrue(user.isTwoFactorEnabled());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("disable2FA removes the secret and disables 2FA")
    void disable2FA_success() {
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("SECRETKEY");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        twoFactorService.disable2FA(1L);

        assertFalse(user.isTwoFactorEnabled());
        assertNull(user.getTwoFactorSecret());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("complete2FALogin returns cookies for valid 2FA code and token")
    void complete2FALogin_success() {
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("SECRETKEY");

        when(jwtUtils.validateTwoFactorToken("2fa-token")).thenReturn(true);
        when(jwtUtils.getUserNameFromJWTToken("2fa-token")).thenReturn("user1");
        when(userRepository.findByUserName("user1")).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRETKEY", 123456)).thenReturn(true);
        when(refreshTokenService.createRefreshToken("user1")).thenReturn("refresh-token");
        when(refreshTokenService.generateRefreshCookie("refresh-token")).thenReturn(ResponseCookie.from("refresh", "refresh-token").build());

        try (MockedStatic<UserDetailsImpl> userDetails = mockStatic(UserDetailsImpl.class);
             MockedStatic<UserInfoMapper> infoMapper = mockStatic(UserInfoMapper.class)) {

            UserDetailsImpl details = new UserDetailsImpl(1L, "user1", "user1@test.com", "pass", null);
            userDetails.when(() -> UserDetailsImpl.build(user)).thenReturn(details);

            UserInfoResponse response = new UserInfoResponse(1L, "user1", List.of("ROLE_USER"));
            infoMapper.when(() -> UserInfoMapper.toUserInfoResponse(user)).thenReturn(response);

            when(jwtUtils.generateJwtCookie(details)).thenReturn(ResponseCookie.from("jwt", "jwt-token").build());

            AuthenticationResult result = twoFactorService.complete2FALogin("2fa-token", 123456);

            assertNotNull(result);
            assertNotNull(result.getJwtCookie());
            assertNotNull(result.getRefreshCookie());
            assertFalse(result.isNeeds2FA());
        }
    }

    @Test
    @DisplayName("complete2FALogin throws when challenge token has wrong purpose")
    void complete2FALogin_wrongTokenPurpose_throws() {
        when(jwtUtils.validateTwoFactorToken("wrong-token")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> twoFactorService.complete2FALogin("wrong-token", 123456));
        assertTrue(ex.getMessage().contains("Invalid 2FA code"));
    }

    @Test
    @DisplayName("complete2FALogin throws for invalid TOTP code")
    void complete2FALogin_invalidCode_throws() {
        user.setTwoFactorSecret("SECRETKEY");

        when(jwtUtils.validateTwoFactorToken("2fa-token")).thenReturn(true);
        when(jwtUtils.getUserNameFromJWTToken("2fa-token")).thenReturn("user1");
        when(userRepository.findByUserName("user1")).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRETKEY", 999999)).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> twoFactorService.complete2FALogin("2fa-token", 999999));
        assertTrue(ex.getMessage().contains("Invalid 2FA code"));
    }

    @Test
    @DisplayName("generate2FASecret throws when user not found")
    void generate2FASecret_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> twoFactorService.generate2FASecret(99L));
        assertTrue(ex.getMessage().contains("User not found"));
    }

    @Test
    @DisplayName("complete2FALogin throws when user not found by username")
    void complete2FALogin_userNotFound_throws() {
        when(jwtUtils.validateTwoFactorToken("2fa-token")).thenReturn(true);
        when(jwtUtils.getUserNameFromJWTToken("2fa-token")).thenReturn("unknown");
        when(userRepository.findByUserName("unknown")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> twoFactorService.complete2FALogin("2fa-token", 123456));
    }
}
