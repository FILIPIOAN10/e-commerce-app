package com.ecommerce.project.service.gdpr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link GdprExportPurgeService#purgeExpired()} on a fixed delay. Set
 * {@code app.gdpr.purge-enabled=false} to disable (tests drive the purge service
 * directly).
 */
@Slf4j
@Component
public class GdprExportPurgeJob {

    private final GdprExportPurgeService purgeService;
    private final boolean enabled;

    public GdprExportPurgeJob(GdprExportPurgeService purgeService,
                              @Value("${app.gdpr.purge-enabled:true}") boolean enabled) {
        this.purgeService = purgeService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.gdpr.purge-interval-ms:3600000}",
            initialDelayString = "${app.gdpr.purge-initial-delay-ms:120000}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            purgeService.purgeExpired();
        } catch (Exception e) {
            // Never let a bad tick kill the scheduler thread.
            log.error("GDPR export purge failed", e);
        }
    }
}
