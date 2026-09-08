package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.model.GdprExport;
import com.ecommerce.project.model.GdprExportStatus;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.GdprExportRepository;
import com.ecommerce.project.security.redis.GdprTokenService;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.gdpr.GdprExportArchiveWriter;
import com.ecommerce.project.service.gdpr.GdprExportAssembler;
import com.ecommerce.project.service.gdpr.GdprExportData;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxHandler;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.outbox.payload.GdprExportOutboxPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Builds one Art. 15 archive and emails its download link.
 *
 * <p>Deferred to the outbox rather than done in the request because assembling a
 * whole account is unbounded work, and because the customer is owed the export
 * even if the process dies mid-build — the row survives, the dispatcher retries.
 *
 * <p>Idempotent, as every handler must be: a redelivery of an already-built
 * archive re-sends the link rather than rebuilding, and issuing that link
 * invalidates the previous one. An archive that expired before the handler ran
 * is dropped — resurrecting a copy of someone's personal data past its own TTL
 * would be the wrong kind of diligence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GdprExportHandler implements OutboxHandler {

    private final GdprExportRepository gdprExportRepository;
    private final GdprExportAssembler assembler;
    private final GdprExportArchiveWriter archiveWriter;
    private final GdprTokenService gdprTokenService;
    private final EmailService emailService;
    private final OutboxPayloadCodec payloadCodec;
    private final TransactionTemplate transactionTemplate;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.gdpr.export-ttl-days:7}")
    private long exportTtlDays;

    @Override
    public String eventType() {
        return OutboxEventTypes.GDPR_EXPORT_REQUESTED;
    }

    /**
     * Two phases, and the split is the point.
     *
     * <p>The archive build genuinely needs a transaction: it reads
     * {@code export.getUser()} — a LAZY {@code @ManyToOne} — walks the whole
     * account through the assembler, and persists {@code markReady} /
     * {@code purge} by dirty checking. That used to ride on the transaction the
     * dispatcher held open around the entire batch; the dispatcher no longer has
     * one, so without a transaction here the entity would come back detached, the
     * lazy read would throw, and the writes would be silently dropped — the same
     * trap that broke the address book in 302618e.
     *
     * <p>The email send must stay <em>outside</em> it. SMTP is the slow, remote,
     * unbounded part, and holding a pooled connection across it is the whole
     * reason the dispatcher stopped wrapping handlers in the first place. Hence
     * {@link TransactionTemplate} rather than {@code @Transactional} on the
     * method: the boundary has to fall in the middle, not around the edge.
     */
    @Override
    public void handle(String payload) {
        GdprExportOutboxPayload data = payloadCodec.deserialize(payload, GdprExportOutboxPayload.class);

        Long readyExportId = transactionTemplate.execute(status -> buildArchive(data));
        if (readyExportId == null) {
            return; // gone or expired — nothing to send
        }

        String token = gdprTokenService.issueExportToken(readyExportId);
        emailService.sendGdprExportReadyEmail(
                data.recipientEmail(),
                data.recipientName(),
                frontendUrl + "/gdpr/export/download?token=" + token,
                exportTtlDays);
    }

    /**
     * @return the id of an archive that is ready to be linked, or {@code null}
     *         when the export no longer exists or expired before it could be built
     */
    private Long buildArchive(GdprExportOutboxPayload data) {
        GdprExport export = gdprExportRepository.findById(data.exportId()).orElse(null);
        if (export == null) {
            log.warn("GDPR export {} no longer exists; dropping event", data.exportId());
            return null;
        }
        if (export.getExpiresAt().isBefore(Instant.now())) {
            log.warn("GDPR export {} expired before it could be built; dropping event", export.getId());
            export.purge();
            return null;
        }

        if (export.getStatus() != GdprExportStatus.READY) {
            User user = export.getUser();
            List<GdprExportData.Section> sections = assembler.assemble(user);
            export.markReady(archiveWriter.write(user.getUserId(), sections));
            log.info("Built GDPR export {} for user {} ({} bytes)",
                    export.getId(), user.getUserId(), export.getByteSize());
        }
        return export.getId();
    }
}
