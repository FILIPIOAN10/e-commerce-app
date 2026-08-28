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
 * Signed, expiring tokens for abandoned-cart recovery links. Same shape as
 * {@link PasswordResetService}: a JWT carrying {@code cartId|tokenId} plus a
 * Redis marker so a token can be single-use and revoked. A raw cart id in the
 * link would be an IDOR — this is not that.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartRecoveryTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtils jwtUtils;

    @Value("${app.abandoned-cart.recovery-token-ttl-hours:168}")
    private long tokenTtlHours;

    private static final String KEY_PREFIX = "cart_recovery_token:";
    private static final String PURPOSE = "cart_recovery";

    public String issue(Long cartId) {
        String tokenId = UUID.randomUUID().toString();
        String token = jwtUtils.generateToken(cartId + "|" + tokenId, PURPOSE,
                Duration.ofHours(tokenTtlHours).toMillis());
        redisTemplate.opsForValue().set(KEY_PREFIX + cartId, tokenId, Duration.ofHours(tokenTtlHours));
        return token;
    }

    /** @return the cart id the token was issued for, if the token is valid and unspent */
    public Optional<Long> consume(String token) {
        try {
            Claims claims = jwtUtils.parseClaims(token);
            if (!PURPOSE.equals(claims.get("purpose", String.class))) {
                return Optional.empty();
            }
            String[] parts = claims.getSubject().split("\\|");
            if (parts.length != 2) {
                return Optional.empty();
            }
            Long cartId = Long.valueOf(parts[0]);
            String storedTokenId = redisTemplate.opsForValue().get(KEY_PREFIX + cartId);
            if (storedTokenId == null || !storedTokenId.equals(parts[1])) {
                return Optional.empty();
            }
            redisTemplate.delete(KEY_PREFIX + cartId);
            return Optional.of(cartId);
        } catch (RuntimeException e) {
            log.debug("Rejected cart recovery token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
