package com.ecommerce.project.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link SubscriptionReconciliationService#reconcileStale(int)} on a fixed
 * delay — the backstop for subscription webhooks Stripe stopped retrying.
 * {@code app.subscriptions.sweep.enabled=false} turns it off (the test profile
 * does; tests drive the service directly). Separated from the service for the
 * same reason as {@code PromoCampaignSweepJob}: scheduling is a deployment
 * concern.
 */
@Slf4j
@Component
public class SubscriptionRenewalSweepJob {

    private final SubscriptionReconciliationService reconciliationService;
    private final boolean enabled;
    private final int graceHours;

    public SubscriptionRenewalSweepJob(
            SubscriptionReconciliationService reconciliationService,
            @Value("${app.subscriptions.sweep.enabled:true}") boolean enabled,
            @Value("${app.subscriptions.sweep.grace-hours:24}") int graceHours) {
        this.reconciliationService = reconciliationService;
        this.enabled = enabled;
        this.graceHours = graceHours;
    }

    @Scheduled(
            fixedDelayString = "${app.subscriptions.sweep.interval-ms:3600000}",
            initialDelayString = "${app.subscriptions.sweep.initial-delay-ms:300000}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            reconciliationService.reconcileStale(graceHours);
        } catch (Exception e) {
            log.error("Subscription renewal sweep failed", e);
        }
    }
}
