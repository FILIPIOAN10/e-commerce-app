package com.ecommerce.project.security.redis;

import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.payload.DeviceSessionResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${spring.ecom.app.refreshTokenCookieName}")
    private String refreshTokenCookieName;

    @Value("${spring.app.refreshTokenExpirationMs:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final String KEY_PREFIX = "refresh:";
    private static final String SESSION_PREFIX = "session:";
    private static final String USER_SESSIONS_PREFIX = "user_sessions:";
    private static final Set<String> NON_SECURE_PROFILES = Set.of("dev", "ci");

    public String createRefreshToken(String username) {
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Map<String, String> info = extractRequestInfo();

        redisTemplate.opsForValue().set(
                KEY_PREFIX + token,
                username,
                Duration.ofMillis(refreshTokenExpirationMs)
        );

        Map<String, String> session = new HashMap<>();
        session.put("username", username);
        session.put("deviceInfo", info.getOrDefault("deviceInfo", "Unknown device"));
        session.put("ipAddress", info.getOrDefault("ipAddress", "unknown"));
        session.put("createdAt", String.valueOf(now));
        session.put("lastUsedAt", String.valueOf(now));

        String sessionKey = SESSION_PREFIX + token;
        redisTemplate.opsForHash().putAll(sessionKey, session);
        redisTemplate.expire(sessionKey, Duration.ofMillis(refreshTokenExpirationMs));

        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        redisTemplate.opsForSet().add(userSessionsKey, token);
        redisTemplate.expire(userSessionsKey, Duration.ofMillis(refreshTokenExpirationMs));

        return token;
    }

    public String validateAndGetUsername(String token) {
        String username = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (username == null) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_PREFIX + token))) {
            redisTemplate.opsForHash().put(SESSION_PREFIX + token, "lastUsedAt", String.valueOf(System.currentTimeMillis()));
        }
        return username;
    }

    public String rotate(String oldToken) {
        String username = validateAndGetUsername(oldToken);
        delete(oldToken);
        return createRefreshToken(username);
    }

    public void delete(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }

    public String getRefreshTokenFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, refreshTokenCookieName);
        return cookie != null ? cookie.getValue() : null;
    }

    public ResponseCookie generateRefreshCookie(String token) {
        return ResponseCookie.from(refreshTokenCookieName, token)
                .path("/api/auth")
                .maxAge(refreshTokenExpirationMs / 1000)
                .httpOnly(true)
                .secure(!NON_SECURE_PROFILES.contains(activeProfile))
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie getCleanRefreshCookie() {
        return ResponseCookie.from(refreshTokenCookieName, "")
                .path("/api/auth")
                .maxAge(0)
                .httpOnly(true)
                .secure(!NON_SECURE_PROFILES.contains(activeProfile))
                .sameSite("Lax")
                .build();
    }

    public List<com.ecommerce.project.payload.DeviceSessionResponse> getSessions(String username, String currentToken) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        Set<String> tokens = redisTemplate.opsForSet().members(userSessionsKey);
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<DeviceSessionResponse> sessions = new ArrayList<>();
        for (String token : tokens) {
            String sessionKey = SESSION_PREFIX + token;
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey))) {
                redisTemplate.opsForSet().remove(userSessionsKey, token);
                continue;
            }
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(sessionKey);
            sessions.add(new DeviceSessionResponse(
                    token,
                    String.valueOf(entries.get("deviceInfo")),
                    String.valueOf(entries.get("ipAddress")),
                    Long.parseLong(String.valueOf(entries.get("createdAt"))),
                    Long.parseLong(String.valueOf(entries.get("lastUsedAt"))),
                    token.equals(currentToken)
            ));
        }
        return sessions.stream()
                .sorted(Comparator.comparingLong(DeviceSessionResponse::lastUsedAt).reversed())
                .collect(Collectors.toList());
    }

    public void revokeSession(String username, String token) {
        String sessionKey = SESSION_PREFIX + token;
        Object storedUser = redisTemplate.opsForHash().get(sessionKey, "username");
        if (storedUser == null || !username.equals(String.valueOf(storedUser))) {
            throw new InvalidCredentialsException("Session not found or does not belong to the current user");
        }
        delete(token);
        redisTemplate.delete(sessionKey);
        redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + username, token);
    }

    /**
     * Revokes every session for a user, the caller's own included. Used by GDPR
     * erasure — {@link #revokeAllOtherSessions} deliberately keeps one alive, and
     * an erased account must keep none.
     */
    public void revokeAllSessions(String username) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        Set<String> tokens = redisTemplate.opsForSet().members(userSessionsKey);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String token : tokens) {
            delete(token);
            redisTemplate.delete(SESSION_PREFIX + token);
        }
        redisTemplate.delete(userSessionsKey);
    }

    public void revokeAllOtherSessions(String username, String currentToken) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        Set<String> tokens = redisTemplate.opsForSet().members(userSessionsKey);
        if (tokens == null) {
            return;
        }
        for (String token : tokens) {
            if (token.equals(currentToken)) {
                continue;
            }
            delete(token);
            redisTemplate.delete(SESSION_PREFIX + token);
        }
        redisTemplate.opsForSet().remove(userSessionsKey, (Object[]) tokens.toArray(new String[0]));
        if (currentToken != null) {
            redisTemplate.opsForSet().add(userSessionsKey, currentToken);
        }
    }

    private Map<String, String> extractRequestInfo() {
        Map<String, String> info = new HashMap<>();
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            info.put("deviceInfo", "Unknown device");
            info.put("ipAddress", "unknown");
            return info;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String userAgent = request.getHeader("User-Agent");
        info.put("deviceInfo", buildDeviceInfo(userAgent));
        String forwardedFor = request.getHeader("X-Forwarded-For");
        info.put("ipAddress", forwardedFor != null && !forwardedFor.isBlank()
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr());
        return info;
    }

    private String buildDeviceInfo(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent.toLowerCase();
        String type;
        if (ua.contains("android") || ua.contains("iphone") || ua.contains("ipod")) {
            type = "Mobile";
        } else if (ua.contains("ipad") || ua.contains("tablet")) {
            type = "Tablet";
        } else {
            type = "Desktop";
        }
        String browser;
        if (ua.contains("edg")) {
            browser = "Edge";
        } else if (ua.contains("opr") || ua.contains("opera")) {
            browser = "Opera";
        } else if (ua.contains("chrome")) {
            browser = "Chrome";
        } else if (ua.contains("safari")) {
            browser = "Safari";
        } else if (ua.contains("firefox")) {
            browser = "Firefox";
        } else {
            browser = "Browser";
        }
        return type + " - " + browser;
    }
}
