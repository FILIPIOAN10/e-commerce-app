package com.ecommerce.project.service.subscription;

import com.ecommerce.project.model.SubscriptionPlan;
import com.ecommerce.project.model.UserSubscription;
import com.ecommerce.project.repository.UserSubscriptionRepository;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.SubscriptionNoticeOutboxPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleServiceImpl implements SubscriptionLifecycleService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    @Transactional
    public void activateFromCheckout(String checkoutSessionId, String stripeSubscriptionId,
                                     Long periodStartEpochSeconds, Long periodEndEpochSeconds) {
        userSubscriptionRepository.findByStripeCheckoutSessionId(checkoutSessionId).ifPresentOrElse(sub -> {
            sub.setStripeSubscriptionId(stripeSubscriptionId);
            sub.setStatus(SubscriptionStatus.ACTIVE.name());
            if (periodStartEpochSeconds != null) {
                sub.setCurrentPeriodStart(toLocalDateTime(periodStartEpochSeconds));
            }
            if (periodEndEpochSeconds != null) {
                sub.setCurrentPeriodEnd(toLocalDateTime(periodEndEpochSeconds));
            }
            userSubscriptionRepository.save(sub);
            log.info("Subscription {} activated from checkout {}", stripeSubscriptionId, checkoutSessionId);
        }, () -> log.warn("checkout.session.completed for unknown session {}", checkoutSessionId));
    }

    @Override
    @Transactional
    public void renewed(String stripeSubscriptionId, Long periodEndEpochSeconds) {
        findBySubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            if (periodEndEpochSeconds != null) {
                sub.setCurrentPeriodEnd(toLocalDateTime(periodEndEpochSeconds));
            }
            // A successful charge clears a dunning state.
            if (SubscriptionStatus.PAST_DUE.name().equals(sub.getStatus())
                    || SubscriptionStatus.UNPAID.name().equals(sub.getStatus())) {
                sub.setStatus(SubscriptionStatus.ACTIVE.name());
            }
            userSubscriptionRepository.save(sub);
            log.info("Subscription {} renewed; period end now {}", stripeSubscriptionId, sub.getCurrentPeriodEnd());
        });
    }

    @Override
    @Transactional
    public void paymentFailed(String stripeSubscriptionId, Long nextAttemptEpochSeconds) {
        findBySubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            if (SubscriptionStatus.CANCELED.name().equals(sub.getStatus())) {
                return; // nothing to dun on a dead subscription
            }
            sub.setStatus(SubscriptionStatus.PAST_DUE.name());
            userSubscriptionRepository.save(sub);
            outboxEventPublisher.publish(OutboxEventTypes.SUBSCRIPTION_PAYMENT_FAILED,
                    new SubscriptionNoticeOutboxPayload(sub.getEmail(), planName(sub)));
            log.info("Subscription {} past due; next Stripe retry {}", stripeSubscriptionId,
                    nextAttemptEpochSeconds != null ? toLocalDateTime(nextAttemptEpochSeconds) : "unknown");
        });
    }

    @Override
    @Transactional
    public void syncFromStripe(String stripeSubscriptionId, SubscriptionStatus status, Long periodEndEpochSeconds) {
        findBySubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            String previous = sub.getStatus();
            sub.setStatus(status.name());
            if (periodEndEpochSeconds != null) {
                sub.setCurrentPeriodEnd(toLocalDateTime(periodEndEpochSeconds));
            }
            if (status.isEnded() && sub.getCanceledAt() == null) {
                sub.setCanceledAt(LocalDateTime.now());
            }
            userSubscriptionRepository.save(sub);

            // Ended via an update (rather than a delete event) still owes the notice.
            if (status.isEnded() && !SubscriptionStatus.valueOf(safe(previous)).isEnded()) {
                outboxEventPublisher.publish(OutboxEventTypes.SUBSCRIPTION_ENDED,
                        new SubscriptionNoticeOutboxPayload(sub.getEmail(), planName(sub)));
            }
            log.info("Subscription {} synced {} -> {}", stripeSubscriptionId, previous, status);
        });
    }

    @Override
    @Transactional
    public void ended(String stripeSubscriptionId) {
        findBySubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            boolean alreadyEnded = SubscriptionStatus.valueOf(safe(sub.getStatus())).isEnded();
            sub.setStatus(SubscriptionStatus.CANCELED.name());
            if (sub.getCanceledAt() == null) {
                sub.setCanceledAt(LocalDateTime.now());
            }
            userSubscriptionRepository.save(sub);
            if (!alreadyEnded) {
                outboxEventPublisher.publish(OutboxEventTypes.SUBSCRIPTION_ENDED,
                        new SubscriptionNoticeOutboxPayload(sub.getEmail(), planName(sub)));
            }
            log.info("Subscription {} ended", stripeSubscriptionId);
        });
    }

    private java.util.Optional<UserSubscription> findBySubscriptionId(String stripeSubscriptionId) {
        var found = stripeSubscriptionId == null
                ? java.util.Optional.<UserSubscription>empty()
                : userSubscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
        if (found.isEmpty()) {
            log.warn("Stripe event references unknown subscription {}", stripeSubscriptionId);
        }
        return found;
    }

    private static String planName(UserSubscription sub) {
        SubscriptionPlan plan = sub.getPlan();
        return plan != null && plan.getName() != null ? plan.getName() : "your subscription";
    }

    private static String safe(String status) {
        try {
            SubscriptionStatus.valueOf(status);
            return status;
        } catch (IllegalArgumentException | NullPointerException e) {
            return SubscriptionStatus.INCOMPLETE.name();
        }
    }

    private static LocalDateTime toLocalDateTime(long epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }
}
