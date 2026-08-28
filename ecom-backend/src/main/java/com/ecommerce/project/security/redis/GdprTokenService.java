package com.ecommerce.project.security.redis;

import com.ecommerce.project.security.jwt.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Signed, single-use tokens for the two GDPR flows, built on the same shape as
 * {@link CartRecoveryTokenService}: a JWT carrying {@code id|tokenId} plus a
 * Redis marker that makes the token spendable exactly once and revocable before
 * then.
 *
 * <p>Two purposes, deliberately distinct so one cannot be replayed as the other:
 * <ul>
 *   <li>{@code gdpr_export} — fetches a built archive. Lives as long as the
 *       archive does, because the link <em>is</em> the archive's only door.</li>
 *   <li>{@code gdpr_erase} — finalises an erasure. Short-lived: it authorises a
 *       destructive, irreversible act, and its holder has already proved the
 *       account password to get it.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtils jwtUtils;

    @Value("${app.gdpr.export-ttl-days:7}")
    private long exportTtlDays;

    @Value("${app.gdpr.erasure-token-ttl-minutes:60}")
    private long erasureTokenTtlMinutes;

    private static final String EXPORT_KEY_PREFIX = "gdpr_export_token:";
    private static final String EXPORT_PURPOSE = "gdpr_export";

    private static final String ERASURE_KEY_PREFIX = "gdpr_erase_token:";
    private static final String ERASURE_PURPOSE = "gdpr_erase";

    public String issueExportToken(Long exportId) {
        return issue(EXPORT_KEY_PREFIX, EXPORT_PURPOSE, exportId, Duration.ofDays(exportTtlDays));
    }

    /** @return the export id the token was issued for, if valid and unspent */
    public Optional<Long> consumeExportToken(String token) {
        return consume(EXPORT_KEY_PREFIX, EXPORT_PURPOSE, token);
    }

    public String issueErasureToken(Long userId) {
        return issue(ERASURE_KEY_PREFIX, ERASURE_PURPOSE, userId,
                Duration.ofMinutes(erasureTokenTtlMinutes));
    }

    /** @return the user id the token was issued for, if valid and unspent */
    public Optional<Long> consumeErasureToken(String token) {
        return consume(ERASURE_KEY_PREFIX, ERASURE_PURPOSE, token);
    }

    /**
     * Drops a pending erasure token without spending it — used when the account
     * is erased by some other route, so a stale confirmation link cannot linger.
     */
    public void revokeErasureToken(Long userId) {
        redisTemplate.delete(ERASURE_KEY_PREFIX + userId);
    }

    private String issue(String keyPrefix, String purpose, Long id, Duration ttl) {
        String tokenId = UUID.randomUUID().toString();
        String token = jwtUtils.generateToken(id + "|" + tokenId, purpose, ttl.toMillis());
        // Overwrites any previous marker for this id: issuing a new token
        // invalidates the old one rather than leaving two live doors.
        redisTemplate.opsForValue().set(keyPrefix + id, tokenId, ttl);
        return token;
    }

    private Optional<Long> consume(String keyPrefix, String purpose, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = jwtUtils.parseClaims(token);
            if (!purpose.equals(claims.get("purpose", String.class))) {
                return Optional.empty();
            }
            String[] parts = claims.getSubject().split("\\|");
            if (parts.length != 2) {
                return Optional.empty();
            }
            Long id = Long.valueOf(parts[0]);
            String storedTokenId = redisTemplate.opsForValue().get(keyPrefix + id);
            if (storedTokenId == null || !storedTokenId.equals(parts[1])) {
                return Optional.empty();
            }
            redisTemplate.delete(keyPrefix + id);
            return Optional.of(id);
        } catch (RuntimeException e) {
            log.debug("Rejected {} token: {}", purpose, e.getMessage());
            return Optional.empty();
        }
    }
}
