package com.ecommerce.project.service.gdpr;

import com.ecommerce.project.model.GdprExport;
import com.ecommerce.project.repository.GdprExportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Drops the bytes of expired export archives.
 *
 * <p>The retention promise made in the email is only worth as much as the job
 * that keeps it. The row itself stays, marked {@code EXPIRED}, as evidence that
 * the request was served — what leaves is the copy of the customer's data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprExportPurgeService {

    private final GdprExportRepository gdprExportRepository;

    /** @return how many archives were purged */
    @Transactional
    public int purgeExpired() {
        List<GdprExport> expired = gdprExportRepository.findPurgeable(Instant.now());
        expired.forEach(GdprExport::purge);
        if (!expired.isEmpty()) {
            log.info("Purged {} expired GDPR export archive(s)", expired.size());
        }
        return expired.size();
    }
}
