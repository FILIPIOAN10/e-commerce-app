package com.ecommerce.project.service;

import com.ecommerce.project.payload.SubscriptionCheckoutDTO;
import com.ecommerce.project.payload.SubscriptionPlanDTO;
import com.ecommerce.project.payload.UserSubscriptionDTO;

import java.util.List;

public interface SubscriptionService {
    SubscriptionPlanDTO createPlan(SubscriptionPlanDTO planDTO);
    SubscriptionPlanDTO updatePlan(Long planId, SubscriptionPlanDTO planDTO);
    SubscriptionPlanDTO getPlanById(Long planId);
    List<SubscriptionPlanDTO> getActivePlans();
    List<SubscriptionPlanDTO> getAllPlans();
    void deletePlan(Long planId);

    SubscriptionCheckoutDTO createCheckoutSession(Long planId, String email);
    List<UserSubscriptionDTO> getMySubscriptions(String email);
    UserSubscriptionDTO cancelSubscription(Long id, String email);
}
