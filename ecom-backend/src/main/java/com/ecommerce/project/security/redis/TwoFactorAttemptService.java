package com.ecommerce.project.security.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Caps how many times a 2FA challenge may be answered wrongly before the
 * account's second factor locks, the way {@link LoginAttemptService} caps
 * password guesses.
 *
 * <p>Without it the second factor was the <em>weakest</em> step in the login
 * rather than the strongest. {@code POST /api/auth/public/verify-2fa-login} had
 * no rate limit rule, and {@code RateLimitFilter} throttles only paths that
 * match one — an unmatched path passes straight through — so a six-digit code
 * could simply be enumerated. Nothing else was counting either: reaching the
 * challenge means the password already succeeded, and {@code AuthServiceImpl}
 * resets the password counter on success, so the lockout that does exist had
 * just been cleared. The challenge token expires after five minutes, which
 * bounds a single run and nothing else: signing in again mints another.
 *
 * <p>Keyed on the username, not on the challenge token, because a token is
 * free — a lock keyed on one is escaped by asking for the next. The username is
 * the part an attacker cannot vary. That does mean wrong codes can lock an
 * account's 2FA step for {@link #LOCK_DURATION}, but reaching this step at all
 * requires the correct password, so it is strictly harder to trigger than the
 * password lockout already shipping.
 *
 * <p>Deliberately a separate service rather than a second prefix on
 * {@link LoginAttemptService}: the two count different things and must not
 * share a budget, or a wrong password would spend the 2FA allowance.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "2fa_attempts:";

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String username) {
        String attempts = redisTemplate.opsForValue().get(KEY_PREFIX + username);
        return attempts != null && Integer.parseInt(attempts) >= MAX_ATTEMPTS;
    }

    /**
     * Counts one wrong code. The TTL is set on the first failure only, so the
     * window is fifteen minutes from the first bad guess rather than a lock
     * that an attacker keeps alive by continuing to guess.
     */
    public void recordFailedAttempt(String username) {
        String key = KEY_PREFIX + username;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, LOCK_DURATION);
        }
    }

    public void resetAttempts(String username) {
        redisTemplate.delete(KEY_PREFIX + username);
    }

    public int getRemainingAttempts(String username) {
        String attempts = redisTemplate.opsForValue().get(KEY_PREFIX + username);
        if (attempts == null) {
            return MAX_ATTEMPTS;
        }
        return Math.max(MAX_ATTEMPTS - Integer.parseInt(attempts), 0);
    }

    /** Seconds until the lock lifts, or 0 when there is no lock. */
    public long getLockTimeRemaining(String username) {
        Long ttl = redisTemplate.getExpire(KEY_PREFIX + username);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }
}
