package com.ecommerce.project.service.gdpr;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.InvalidCredentialsException;
import com.ecommerce.project.model.GdprExport;
import com.ecommerce.project.model.GdprExportStatus;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.GdprExportRepository;
import com.ecommerce.project.security.redis.GdprTokenService;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.GdprExportOutboxPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The user-facing side of GDPR: the two things a person can ask for, and the
 * checks that stand in front of them.
 *
 * <p>Export is deferred to the outbox — the request only records intent, so a
 * customer never waits on an unbounded assembly and never loses the request to a
 * crash halfway through it.
 *
 * <p>Erasure is two-step by design: password now, emailed link to finalise.
 * Neither factor alone is enough. The password stops someone acting on a session
 * they walked up to; the email link stops a stolen-but-still-valid session from
 * destroying an account, and gives the real owner a message saying it is about
 * to happen. Deletion is the one action here that cannot be undone, so it is the
 * one worth being tedious about.
 */
@Slf4j
@Service
public class GdprService {

    private final GdprExportRepository gdprExportRepository;
    private final GdprErasureService gdprErasureService;
    private final GdprTokenService gdprTokenService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private final long exportTtlDays;
    private final long erasureTokenTtlMinutes;
    private final String frontendUrl;

    public GdprService(GdprExportRepository gdprExportRepository,
                       GdprErasureService gdprErasureService,
                       GdprTokenService gdprTokenService,
                       OutboxEventPublisher outboxEventPublisher,
                       EmailService emailService,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.gdpr.export-ttl-days:7}") long exportTtlDays,
                       @Value("${app.gdpr.erasure-token-ttl-minutes:60}") long erasureTokenTtlMinutes,
                       @Value("${frontend.url:http://localhost:5173}") String frontendUrl) {
        this.gdprExportRepository = gdprExportRepository;
        this.gdprErasureService = gdprErasureService;
        this.gdprTokenService = gdprTokenService;
        this.outboxEventPublisher = outboxEventPublisher;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.exportTtlDays = exportTtlDays;
        this.erasureTokenTtlMinutes = erasureTokenTtlMinutes;
        this.frontendUrl = frontendUrl;
    }

    // ── Art. 15: export ─────────────────────────────────────────────────────

    /**
     * Accepts an export request and enqueues the build.
     *
     * <p>One live archive per user at a time. Asking again while one is pending
     * is answered, not queued — otherwise a bored user could have the server
     * assemble their entire account a hundred times over. Asking again while one
     * is <em>ready</em> re-sends the link for the archive already built, which is
     * also how a customer recovers from a download link they lost: the previous
     * link stops working, the new one arrives by email.
     *
     * @return a message for the caller describing what happened
     */
    @Transactional
    public String requestExport(User user) {
        Instant now = Instant.now();
        Optional<GdprExport> live = gdprExportRepository.findLatestLiveForUser(user.getUserId(), now);

        if (live.isPresent() && live.get().getStatus() == GdprExportStatus.PENDING) {
            return "An export of your data is already being prepared. "
                    + "You will receive a download link by email shortly.";
        }

        GdprExport export = live
                .filter(e -> e.getStatus() == GdprExportStatus.READY)
                .orElseGet(() -> gdprExportRepository.save(
                        new GdprExport(user, now.plus(Duration.ofDays(exportTtlDays)))));

        // Same event either way: the handler rebuilds only when the archive is
        // not already there, and always issues a fresh single-use link.
        outboxEventPublisher.publish(OutboxEventTypes.GDPR_EXPORT_REQUESTED,
                new GdprExportOutboxPayload(
                        export.getId(), user.getUserId(), user.getEmail(), user.getUserName()));

        return "We are preparing your data export. You will receive a download link by email; "
                + "it expires after " + exportTtlDays + " days.";
    }

    /**
     * Exchanges a single-use download token for the archive.
     *
     * <p>Public by necessity — the link arrives by email and the recipient is not
     * necessarily signed in. What makes that safe is the token: signed, scoped to
     * one purpose, spendable once, and dead when the archive expires.
     */
    @Transactional
    public GdprArchive downloadExport(String token) {
        Long exportId = gdprTokenService.consumeExportToken(token)
                .orElseThrow(() -> new APIException(
                        "This download link is invalid, already used, or expired. "
                        + "Request a new export from your account settings."));

        GdprExport export = gdprExportRepository.findById(exportId)
                .orElseThrow(() -> new APIException("This export is no longer available."));

        if (!export.isDownloadable(Instant.now())) {
            throw new APIException("This export is no longer available. Please request a new one.");
        }

        export.setDownloadedAt(Instant.now());
        return new GdprArchive("personal-data-export-" + export.getId() + ".zip", export.getPayload());
    }

    // ── Art. 17: erasure ────────────────────────────────────────────────────

    /**
     * First step: prove the password, receive a confirmation link. Nothing is
     * deleted here.
     */
    @Transactional
    public String requestErasure(User user, String rawPassword) {
        requirePassword(user, rawPassword);

        String token = gdprTokenService.issueErasureToken(user.getUserId());
        emailService.sendGdprErasureConfirmationEmail(
                user.getEmail(),
                user.getUserName(),
                frontendUrl + "/gdpr/erase/confirm?token=" + token,
                erasureTokenTtlMinutes);

        return "Check your email: we sent a link that will permanently delete your account. "
                + "It expires in " + erasureTokenTtlMinutes + " minutes. "
                + "Nothing has been deleted yet.";
    }

    /** Second step: spend the emailed token, and erase. */
    @Transactional
    public String confirmErasure(String token) {
        Long userId = gdprTokenService.consumeErasureToken(token)
                .orElseThrow(() -> new APIException(
                        "This confirmation link is invalid, already used, or expired. "
                        + "Start the deletion again from your account settings."));

        gdprErasureService.erase(userId);
        return "Your account has been deleted. Orders we are legally required to keep have been "
                + "anonymised and can no longer be traced back to you.";
    }

    /**
     * A local account must re-enter its password. An OAuth account has none to
     * re-enter — for those the emailed link is the only factor, which is the same
     * assurance the provider itself gives us for sign-in.
     */
    private void requirePassword(User user, String rawPassword) {
        boolean hasLocalPassword = user.getPassword() != null && !user.getPassword().isBlank();
        if (!hasLocalPassword) {
            log.info("GDPR erasure requested for password-less account {}; "
                     + "relying on email confirmation alone", user.getUserId());
            return;
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidCredentialsException("Enter your password to delete your account");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException("Password is incorrect");
        }
    }
}
