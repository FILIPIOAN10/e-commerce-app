package com.ecommerce.project.service.cart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link AbandonedCartSweepService#sweep()} on a fixed delay. Set
 * {@code app.abandoned-cart.enabled=false} to disable (tests drive the sweep
 * service directly).
 */
@Slf4j
@Component
public class AbandonedCartReminderJob {

    private final AbandonedCartSweepService sweepService;
    private final boolean enabled;

    public AbandonedCartReminderJob(AbandonedCartSweepService sweepService,
                                    @Value("${app.abandoned-cart.enabled:true}") boolean enabled) {
        this.sweepService = sweepService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.abandoned-cart.sweep-interval-ms:900000}",
            initialDelayString = "${app.abandoned-cart.initial-delay-ms:60000}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            int enqueued = sweepService.sweep();
            if (enqueued > 0) {
                log.info("Abandoned-cart sweep enqueued {} reminder(s)", enqueued);
            }
        } catch (Exception e) {
            log.error("Abandoned-cart sweep failed", e);
        }
    }
}
