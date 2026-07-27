package com.ecommerce.project.security.jwt;

import com.ecommerce.project.security.services.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 86_400_000);
        userDetails = new UserDetailsImpl(1L, "alice", "alice@example.com", "password", List.of());
    }

    @Test
    void accessTokenIsNotAcceptedAsTwoFactorChallenge() {
        String accessToken = jwtUtils.generateJwtToken(userDetails);

        assertTrue(jwtUtils.validateJwtToken(accessToken));
        assertFalse(jwtUtils.validateTwoFactorToken(accessToken));
        assertEquals("alice", jwtUtils.getUserNameFromJWTToken(accessToken));
    }

    @Test
    void twoFactorChallengeCannotAuthenticateApiRequests() {
        String challengeToken = jwtUtils.generateTwoFactorToken(userDetails);

        assertTrue(jwtUtils.validateTwoFactorToken(challengeToken));
        assertFalse(jwtUtils.validateJwtToken(challengeToken));
        assertEquals("alice", jwtUtils.getUserNameFromJWTToken(challengeToken));
    }
}
