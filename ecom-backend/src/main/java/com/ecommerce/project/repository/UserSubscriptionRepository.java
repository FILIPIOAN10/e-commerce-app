package com.ecommerce.project.repository;

import com.ecommerce.project.model.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    List<UserSubscription> findByEmailOrderByCreatedAtDesc(String email);
    Optional<UserSubscription> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    List<UserSubscription> findByPlanPlanId(Long planId);
}
