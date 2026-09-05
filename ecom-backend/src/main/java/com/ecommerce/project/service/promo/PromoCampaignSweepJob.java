package com.ecommerce.project.service.promo;

import com.ecommerce.project.service.PromoCampaignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link PromoCampaignService#applyActiveCampaigns()} on a fixed delay.
 * Set {@code app.promo.enabled=false} to disable (tests drive the service
 * directly, so a background sweep cannot race their assertions).
 *
 * <p>Separate from the service for the same reason as
 * {@code AbandonedCartReminderJob}: scheduling is a deployment concern, and a
 * test that wants to observe one pass should not have to defeat a timer to do
 * it. {@code fixedDelay}, not {@code fixedRate}, so a slow pass cannot overlap
 * itself.
 */
@Slf4j
@Component
public class PromoCampaignSweepJob {

    private final PromoCampaignService promoCampaignService;
    private final boolean enabled;

    public PromoCampaignSweepJob(PromoCampaignService promoCampaignService,
                                 @Value("${app.promo.enabled:true}") boolean enabled) {
        this.promoCampaignService = promoCampaignService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.promo.sweep-interval-ms:60000}",
            initialDelayString = "${app.promo.sweep-initial-delay-ms:30000}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            promoCampaignService.applyActiveCampaigns();
        } catch (Exception e) {
            log.error("Promo campaign sweep failed", e);
        }
    }
}
