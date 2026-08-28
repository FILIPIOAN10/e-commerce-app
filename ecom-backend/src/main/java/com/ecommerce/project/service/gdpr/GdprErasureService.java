package com.ecommerce.project.service.gdpr;

import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.redis.GdprTokenService;
import com.ecommerce.project.security.redis.RefreshTokenService;
import com.ecommerce.project.service.AdminAuditLogService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Art. 17, "right to be forgotten", as the law actually allows it to be
 * implemented in a shop.
 *
 * <p>Two categories, and the split is the whole design:
 * <ul>
 *   <li><strong>Deleted outright</strong> — cart, wishlist, reviews, questions,
 *       notifications, activity log, abandoned-cart reminders, recently-viewed.
 *       Nothing obliges us to keep any of it.</li>
 *   <li><strong>Anonymised in place</strong> — orders, order lines, payments,
 *       invoices, returns. Fiscal law requires the transaction record for years;
 *       what it does not require is knowing who made it. So the rows stay, with
 *       every identifier replaced, and the amounts, dates and line items — which
 *       are not personal data once detached from a person — left intact so the
 *       books still balance.</li>
 * </ul>
 *
 * <p>The user row itself is a tombstone rather than a deletion: retained orders
 * hold foreign keys into it, and {@code erased = true} is what stops it ever
 * authenticating again (see {@code UserDetailsImpl.isEnabled()}).
 *
 * <p>All of it in one transaction. A half-erased account — cart gone, email
 * still on the orders — is the one outcome that would be worse than not starting.
 *
 * <p>Bulk JPQL is used throughout: this runs once per account and must not load
 * an unbounded object graph into memory to delete it. The persistence context is
 * cleared afterwards so no caller sees a stale copy of what was just rewritten.
 */
@Slf4j
@Service
public class GdprErasureService {

    /** Domain that can never receive mail — RFC 2606 reserves {@code .invalid}. */
    private static final String ANONYMISED_DOMAIN = "@anonymised.invalid";
    private static final String REDACTED = "REDACTED";

    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final GdprTokenService gdprTokenService;
    private final StringRedisTemplate redisTemplate;
    private final AdminAuditLogService adminAuditLogService;

    public GdprErasureService(EntityManager entityManager,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              RefreshTokenService refreshTokenService,
                              GdprTokenService gdprTokenService,
                              StringRedisTemplate redisTemplate,
                              AdminAuditLogService adminAuditLogService) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.gdprTokenService = gdprTokenService;
        this.redisTemplate = redisTemplate;
        this.adminAuditLogService = adminAuditLogService;
    }

    /**
     * Erases one account. Idempotent: an already-erased account is a no-op, so a
     * double-submitted confirmation link cannot fail loudly at the customer.
     */
    @Transactional
    public void erase(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        if (user.isErased()) {
            log.info("GDPR erasure requested for already-erased user {}; nothing to do", userId);
            return;
        }

        String originalEmail = user.getEmail();
        String originalUsername = user.getUserName();
        String pseudonym = pseudonymFor(userId);

        revokeAccess(userId, originalUsername);
        deletePersonalContent(user, originalEmail, originalUsername);
        anonymiseRetainedRecords(originalEmail, pseudonym);
        tombstoneAccount(user, pseudonym);

        // Nothing below may read a stale pre-erasure copy of what the bulk
        // statements above rewrote behind the persistence context's back.
        entityManager.flush();
        entityManager.clear();

        adminAuditLogService.logGdprErasure(userId, pseudonym);
        log.info("GDPR erasure completed for user {} (pseudonym {})", userId, pseudonym);
    }

    /** Kills every live session and any pending erasure link for the account. */
    private void revokeAccess(Long userId, String username) {
        // Including the session that asked: an erased account keeps no doors open.
        refreshTokenService.revokeAllSessions(username);
        gdprTokenService.revokeErasureToken(userId);
        redisTemplate.delete("recently_viewed:" + userId);
    }

    /** Everything we are free to destroy. */
    private void deletePersonalContent(User user, String email, String username) {
        Long userId = user.getUserId();

        // Cart graph, innermost first — cart_reminder and cart_items both hold
        // a FK to carts.
        execute("""
                DELETE FROM CartReminder r
                WHERE r.cart.cartId IN (SELECT c.cartId FROM Cart c WHERE c.user.userId = :userId)
                """, "userId", userId);
        execute("""
                DELETE FROM CartItem ci
                WHERE ci.cart.cartId IN (SELECT c.cartId FROM Cart c WHERE c.user.userId = :userId)
                """, "userId", userId);
        execute("DELETE FROM Cart c WHERE c.user.userId = :userId", "userId", userId);

        execute("DELETE FROM Wishlist w WHERE w.user.userId = :userId", "userId", userId);

        // Reviews and questions carry the customer's own words and a NOT NULL
        // user_id, so there is nothing to anonymise them to — they go.
        execute("DELETE FROM Review r WHERE r.user.userId = :userId", "userId", userId);
        execute("DELETE FROM ProductQuestion q WHERE q.user.userId = :userId", "userId", userId);

        execute("DELETE FROM AppNotification n WHERE n.recipientEmail = :email", "email", email);
        execute("DELETE FROM UserActivityLog l WHERE l.username = :username", "username", username);

        // Addresses not referenced by a retained order have no reason to survive;
        // the rest are redacted in place below.
        execute("""
                DELETE FROM Address a
                WHERE a.user.userId = :userId
                  AND NOT EXISTS (SELECT 1 FROM Order o WHERE o.address.addressId = a.addressId)
                """, "userId", userId);
    }

    /** Everything the tax authority makes us keep, stripped of the person. */
    private void anonymiseRetainedRecords(String email, String pseudonym) {
        String anonymisedEmail = pseudonym + ANONYMISED_DOMAIN;

        // Blank the gateway's free-text response first: it is matched through
        // the orders that are about to stop pointing at this email.
        entityManager.createQuery("""
                        UPDATE Payment p SET p.pgResponseMessage = ''
                        WHERE p.paymentId IN (SELECT o.payment.paymentId FROM Order o
                                              WHERE lower(o.email) = lower(:email) AND o.payment IS NOT NULL)
                        """)
                .setParameter("email", email)
                .executeUpdate();

        // Shipping addresses on retained orders: the order must keep *an*
        // address (the column is part of the record), but not a real one.
        entityManager.createQuery("""
                        UPDATE Address a
                        SET a.street = :redacted, a.buildingName = :redacted, a.city = :redacted,
                            a.state = :redacted, a.country = :redacted, a.pincode = :redacted, a.user = NULL
                        WHERE a.addressId IN (SELECT o.address.addressId FROM Order o
                                              WHERE lower(o.email) = lower(:email) AND o.address IS NOT NULL)
                        """)
                .setParameter("redacted", REDACTED)
                .setParameter("email", email)
                .executeUpdate();

        execute("UPDATE ReturnRequest r SET r.userEmail = :anonymised WHERE lower(r.userEmail) = lower(:email)",
                "anonymised", anonymisedEmail, "email", email);
        execute("UPDATE UserSubscription s SET s.email = :anonymised WHERE lower(s.email) = lower(:email)",
                "anonymised", anonymisedEmail, "email", email);

        // Last, because every statement above locates its rows through it.
        execute("UPDATE Order o SET o.email = :anonymised WHERE lower(o.email) = lower(:email)",
                "anonymised", anonymisedEmail, "email", email);
    }

    /**
     * Replaces the identifiers on the user row. The password becomes a hash of a
     * value nobody holds — belt and braces behind {@code erased}, so the row
     * cannot authenticate even if some future code path forgets to check the flag.
     */
    private void tombstoneAccount(User user, String pseudonym) {
        user.setUserName(truncate(pseudonym, 20));
        user.setEmail(pseudonym + ANONYMISED_DOMAIN);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setPasswordHint(null);
        user.setPhone(null);
        user.setAvatarUrl(null);
        user.setTwoFactorSecret(null);
        user.setTwoFactorEnabled(false);
        user.setProviderId(null);
        user.setMarketingOptIn(false);
        user.setErased(true);
        user.setErasedAt(Instant.now());
        userRepository.save(user);
    }

    /**
     * A stable, non-reversible handle for the erased account. Deterministic on
     * purpose: the retained orders, returns and subscriptions keep pointing at
     * the same pseudonym, so accounting can still tell "these belong together"
     * without being able to tell whose they were.
     */
    private String pseudonymFor(Long userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("gdpr-erasure:" + userId).getBytes(StandardCharsets.UTF_8));
            return "deleted-" + HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the platform; unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void execute(String jpql, Object... nameValuePairs) {
        var query = entityManager.createQuery(jpql);
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            query.setParameter(String.valueOf(nameValuePairs[i]), nameValuePairs[i + 1]);
        }
        query.executeUpdate();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
