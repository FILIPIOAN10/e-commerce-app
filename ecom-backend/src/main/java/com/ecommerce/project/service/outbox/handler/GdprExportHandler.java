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

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.gdpr.export-ttl-days:7}")
    private long exportTtlDays;

    @Override
    public String eventType() {
        return OutboxEventTypes.GDPR_EXPORT_REQUESTED;
    }

    @Override
    public void handle(String payload) {
        GdprExportOutboxPayload data = payloadCodec.deserialize(payload, GdprExportOutboxPayload.class);

        GdprExport export = gdprExportRepository.findById(data.exportId()).orElse(null);
        if (export == null) {
            log.warn("GDPR export {} no longer exists; dropping event", data.exportId());
            return;
        }
        if (export.getExpiresAt().isBefore(Instant.now())) {
            log.warn("GDPR export {} expired before it could be built; dropping event", export.getId());
            export.purge();
            return;
        }

        if (export.getStatus() != GdprExportStatus.READY) {
            User user = export.getUser();
            List<GdprExportData.Section> sections = assembler.assemble(user);
            export.markReady(archiveWriter.write(user.getUserId(), sections));
            log.info("Built GDPR export {} for user {} ({} bytes)",
                    export.getId(), user.getUserId(), export.getByteSize());
        }

        String token = gdprTokenService.issueExportToken(export.getId());
        emailService.sendGdprExportReadyEmail(
                data.recipientEmail(),
                data.recipientName(),
                frontendUrl + "/gdpr/export/download?token=" + token,
                exportTtlDays);
    }
}
