package com.ecommerce.project.service;

import com.ecommerce.project.exception.*;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.redis.EmailVerificationService;
import com.ecommerce.project.security.redis.LoginAttemptService;
import com.ecommerce.project.security.redis.RefreshTokenService;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.security.services.UserDetailsServiceImpl;
import com.ecommerce.project.service.impl.AuthServiceImpl;
import com.ecommerce.project.util.UserInfoMapper;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthServiceImpl tests")
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtils jwtUtils;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder encoder;
    @Mock private RoleRepository roleRepository;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private EmailService emailService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private UserDetailsServiceImpl userDetailsService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;
    private UserDetailsImpl userDetails;
    private LoginRequest loginRequest;
    private SignupRequest signupRequest;
    private Role userRole;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "skipVerificationEmail", true);

        user = new User();
        user.setUserId(1L);
        user.setUserName("user1");
        user.setEmail("user1@test.com");
        user.setPassword("encoded");
        user.setVerified(true);
        user.setTwoFactorEnabled(false);

        userDetails = new UserDetailsImpl(1L, "user1", "user1@test.com", "encoded",
                Set.of(new SimpleGrantedAuthority("ROLE_USER")));

        loginRequest = new LoginRequest();
        loginRequest.setUsername("user1");
        loginRequest.setPassword("password1");

        signupRequest = new SignupRequest();
        signupRequest.setUsername("newuser");
        signupRequest.setEmail("newuser@test.com");
        signupRequest.setPassword("Password1!");

        userRole = new Role();
        userRole.setRoleId(1);
        userRole.setRoleName(AppRole.ROLE_USER);
    }

    @Test
    @DisplayName("login succeeds for valid credentials")
    void login_success() {
        when(loginAttemptService.isLocked("user1")).thenReturn(false);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);

        when(userRepository.findByUserName("user1")).thenReturn(Optional.of(user));
        when(jwtUtils.generateJwtCookie(userDetails)).thenReturn(ResponseCookie.from("jwt", "jwt-value").build());
        when(refreshTokenService.createRefreshToken("user1")).thenReturn("refresh");
        when(refreshTokenService.generateRefreshCookie("refresh")).thenReturn(ResponseCookie.from("refresh", "refresh-value").build());

        try (MockedStatic<UserInfoMapper> infoMapper = mockStatic(UserInfoMapper.class)) {
            UserInfoResponse response = new UserInfoResponse(1L, "user1", List.of("ROLE_USER"));
            infoMapper.when(() -> UserInfoMapper.toUserInfoResponse(user)).thenReturn(response);

            AuthenticationResult result = authService.login(loginRequest);

            assertNotNull(result);
            assertNotNull(result.getJwtCookie());
            assertNotNull(result.getRefreshCookie());
            assertFalse(result.isNeeds2FA());
            assertEquals(response, result.getResponse());
        }
    }

    @Test
    @DisplayName("login throws when account is locked")
    void login_accountLocked_throws() {
        when(loginAttemptService.isLocked("user1")).thenReturn(true);
        when(loginAttemptService.getLockTimeRemaining("user1")).thenReturn(600L);

        AccountLockedException ex = assertThrows(AccountLockedException.class,
                () -> authService.login(loginRequest));
        assertTrue(ex.getMessage().contains("Account locked"));
    }

    @Test
    @DisplayName("login throws for wrong credentials with remaining attempts")
    void login_wrongCredentials_withRemaining_throws() {
        when(loginAttemptService.isLocked("user1")).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));
        when(loginAttemptService.getRemainingAttempts("user1")).thenReturn(2);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(loginRequest));
        assertTrue(ex.getMessage().contains("2 attempts remaining"));
    }

    @Test
    @DisplayName("login throws for wrong credentials and locks the account")
    void login_wrongCredentials_locksAccount_throws() {
        when(loginAttemptService.isLocked("user1")).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));
        when(loginAttemptService.getRemainingAttempts("user1")).thenReturn(0);

        AccountLockedException ex = assertThrows(AccountLockedException.class,
                () -> authService.login(loginRequest));
        assertTrue(ex.getMessage().contains("Account locked"));
    }

    @Test
    @DisplayName("login throws when account is not verified")
    void login_notVerified_throws() {
        user.setVerified(false);

        when(loginAttemptService.isLocked("user1")).thenReturn(false);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);

        when(userRepository.findByUserName("user1")).thenReturn(Optional.of(user));

        assertThrows(AccountNotVerifiedException.class, () -> authService.login(loginRequest));
    }

    @Test
    @DisplayName("login returns 2FA challenge when 2FA is enabled")
    void login_2faEnabled_returnsTempToken() {
        user.setTwoFactorEnabled(true);

        when(loginAttemptService.isLocked("user1")).thenReturn(false);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);

        when(userRepository.findByUserName("user1")).thenReturn(Optional.of(user));
        when(jwtUtils.generateTwoFactorToken(userDetails)).thenReturn("2fa-token");

        AuthenticationResult result = authService.login(loginRequest);

        assertNotNull(result);
        assertTrue(result.isNeeds2FA());
        assertEquals("2fa-token", result.getTemp2FAToken());
        assertNull(result.getJwtCookie());
    }

    @Test
    @DisplayName("register throws when username is duplicate")
    void register_duplicateUsername_throws() {
        when(userRepository.existsByUserName("newuser")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(signupRequest));
    }

    @Test
    @DisplayName("register throws when email is duplicate")
    void register_duplicateEmail_throws() {
        when(userRepository.existsByUserName("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@test.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(signupRequest));
    }

    @Test
    @DisplayName("register creates user when inputs are valid")
    void register_success() {
        when(userRepository.existsByUserName("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@test.com")).thenReturn(false);
        when(encoder.encode("Password1!")).thenReturn("encoded-pass");
        when(roleRepository.findByRoleName(AppRole.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = authService.register(signupRequest);

        assertEquals("User registered successfully!", response.getMessage());
        verify(userRepository, atLeast(2)).save(any(User.class));
    }

    @Test
    @DisplayName("refreshAccessToken rotates and returns new tokens")
    void refreshAccessToken_success() {
        when(refreshTokenService.rotate("old-refresh")).thenReturn("new-refresh");
        when(refreshTokenService.validateAndGetUsername("new-refresh")).thenReturn("user1");
        when(userRepository.findByUserName("user1")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("user1")).thenReturn(userDetails);
        when(jwtUtils.generateJwtCookie(userDetails)).thenReturn(ResponseCookie.from("jwt", "new-jwt").build());
        when(refreshTokenService.generateRefreshCookie("new-refresh")).thenReturn(ResponseCookie.from("refresh", "new-refresh").build());

        try (MockedStatic<UserInfoMapper> infoMapper = mockStatic(UserInfoMapper.class)) {
            UserInfoResponse response = new UserInfoResponse(1L, "user1", List.of("ROLE_USER"));
            infoMapper.when(() -> UserInfoMapper.toUserInfoResponse(user)).thenReturn(response);

            AuthenticationResult result = authService.refreshAccessToken("old-refresh");

            assertNotNull(result);
            assertNotNull(result.getJwtCookie());
            assertNotNull(result.getRefreshCookie());
            assertEquals(response, result.getResponse());
        }
    }

    @Test
    @DisplayName("refreshAccessToken throws for blank token")
    void refreshAccessToken_blank_throws() {
        assertThrows(InvalidCredentialsException.class, () -> authService.refreshAccessToken(""));
    }

    @Test
    @DisplayName("refreshAccessToken throws for null token")
    void refreshAccessToken_null_throws() {
        assertThrows(InvalidCredentialsException.class, () -> authService.refreshAccessToken(null));
    }

    @Test
    @DisplayName("logoutUser returns clean JWT cookie")
    void logoutUser_success() {
        when(jwtUtils.getCleanJwtCookie()).thenReturn(ResponseCookie.from("jwt", "").build());

        ResponseCookie cookie = authService.logoutUser();

        assertNotNull(cookie);
    }
}
