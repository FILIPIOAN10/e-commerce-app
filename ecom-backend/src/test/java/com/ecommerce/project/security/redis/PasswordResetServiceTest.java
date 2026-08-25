package com.ecommerce.project.security.redis;

import com.ecommerce.project.security.jwt.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PasswordResetService (Redis token) tests")
class PasswordResetServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private static final String EMAIL = "user@test.com";
    private static final String TOKEN_ID = "token-123";
    private static final String TOKEN = "jwt-token";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "tokenExpirationMinutes", 15L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("generateResetToken creates and stores token")
    void generateResetToken_success() {
        when(jwtUtils.generateToken(anyString(), eq("password_reset"), anyLong())).thenReturn(TOKEN);

        String result = passwordResetService.generateResetToken(EMAIL);

        assertEquals(TOKEN, result);
        verify(valueOps).set(eq("reset_token:" + EMAIL), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("validateResetToken returns true for a valid token")
    void validateResetToken_valid_returnsTrue() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(EMAIL + "|" + TOKEN_ID);
        when(claims.get("purpose", String.class)).thenReturn("password_reset");

        when(jwtUtils.parseClaims(TOKEN)).thenReturn(claims);
        when(valueOps.get("reset_token:" + EMAIL)).thenReturn(TOKEN_ID);

        boolean valid = passwordResetService.validateResetToken(TOKEN, EMAIL);

        assertTrue(valid);
    }

    @Test
    @DisplayName("validateResetToken returns false for an expired token")
    void validateResetToken_expired_returnsFalse() {
        when(jwtUtils.parseClaims(TOKEN)).thenThrow(new ExpiredJwtException(null, null, "expired"));

        boolean valid = passwordResetService.validateResetToken(TOKEN, EMAIL);

        assertFalse(valid);
    }

    @Test
    @DisplayName("validateResetToken returns false for a reused/invalidated token")
    void validateResetToken_reused_returnsFalse() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(EMAIL + "|" + TOKEN_ID);
        when(claims.get("purpose", String.class)).thenReturn("password_reset");

        when(jwtUtils.parseClaims(TOKEN)).thenReturn(claims);
        when(valueOps.get("reset_token:" + EMAIL)).thenReturn("different-token-id");

        boolean valid = passwordResetService.validateResetToken(TOKEN, EMAIL);

        assertFalse(valid);
    }

    @Test
    @DisplayName("validateResetToken returns false for wrong token purpose")
    void validateResetToken_wrongPurpose_returnsFalse() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(EMAIL + "|" + TOKEN_ID);
        when(claims.get("purpose", String.class)).thenReturn("other_purpose");

        when(jwtUtils.parseClaims(TOKEN)).thenReturn(claims);

        boolean valid = passwordResetService.validateResetToken(TOKEN, EMAIL);

        assertFalse(valid);
    }

    @Test
    @DisplayName("validateResetToken returns false for malformed subject")
    void validateResetToken_malformedSubject_returnsFalse() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("malformed");
        when(claims.get("purpose", String.class)).thenReturn("password_reset");

        when(jwtUtils.parseClaims(TOKEN)).thenReturn(claims);

        boolean valid = passwordResetService.validateResetToken(TOKEN, EMAIL);

        assertFalse(valid);
    }

    @Test
    @DisplayName("extractEmailFromToken returns email from subject")
    void extractEmailFromToken_success() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(EMAIL + "|" + TOKEN_ID);

        when(jwtUtils.parseClaims(TOKEN)).thenReturn(claims);

        String email = passwordResetService.extractEmailFromToken(TOKEN);

        assertEquals(EMAIL, email);
    }

    @Test
    @DisplayName("invalidateToken removes token from Redis")
    void invalidateToken_success() {
        passwordResetService.invalidateToken(EMAIL);

        verify(redisTemplate).delete("reset_token:" + EMAIL);
    }
}
