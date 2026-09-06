package com.ecommerce.project.repository;

import com.ecommerce.project.model.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    List<UserSubscription> findByEmailOrderByCreatedAtDesc(String email);
    Optional<UserSubscription> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    List<UserSubscription> findByPlanPlanId(Long planId);

    /**
     * Live subscriptions whose current period ended before {@code cutoff} and
     * whose renewal we never heard about — the missed-webhook backstop reconciles
     * these against Stripe.
     */
    @Query("""
            select s from UserSubscription s
            where s.status in :statuses
              and s.stripeSubscriptionId is not null
              and s.currentPeriodEnd is not null
              and s.currentPeriodEnd < :cutoff
            """)
    List<UserSubscription> findStale(@Param("statuses") Collection<String> statuses,
                                     @Param("cutoff") LocalDateTime cutoff);
}
