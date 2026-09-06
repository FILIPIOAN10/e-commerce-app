package com.ecommerce.project.service.subscription;

import com.ecommerce.project.model.UserSubscription;
import com.ecommerce.project.repository.UserSubscriptionRepository;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.ecommerce.project.service.subscription.StripeSubscriptionEvents.periodEndEpochSeconds;

/**
 * The missed-webhook backstop. A webhook delivery Stripe never retried past, or
 * one lost to a deploy, can leave a subscription's period end in the past while
 * it is still ACTIVE / PAST_DUE locally. This walks those rows and asks Stripe
 * for the truth. Runs from {@code SubscriptionRenewalSweepJob}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReconciliationService {

    private static final List<String> LIVE_STATUSES = List.of(
            SubscriptionStatus.ACTIVE.name(), SubscriptionStatus.PAST_DUE.name());

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionLifecycleService lifecycle;

    @Value("${stripe.secret.key:}")
    private String stripeApiKey;

    /**
     * Reconciles every live subscription whose period ended more than
     * {@code graceHours} ago. Returns how many rows it checked.
     */
    public int reconcileStale(int graceHours) {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            log.debug("Stripe not configured — skipping subscription reconciliation");
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(graceHours);
        List<UserSubscription> stale = userSubscriptionRepository.findStale(LIVE_STATUSES, cutoff);
        for (UserSubscription sub : stale) {
            try {
                Subscription remote = Subscription.retrieve(sub.getStripeSubscriptionId());
                lifecycle.syncFromStripe(
                        remote.getId(),
                        SubscriptionStatus.fromStripe(remote.getStatus()),
                        periodEndEpochSeconds(remote));
            } catch (Exception e) {
                log.warn("Could not reconcile subscription {} against Stripe: {}",
                        sub.getStripeSubscriptionId(), e.getMessage());
            }
        }
        if (!stale.isEmpty()) {
            log.info("Subscription reconciliation checked {} stale rows", stale.size());
        }
        return stale.size();
    }
}
