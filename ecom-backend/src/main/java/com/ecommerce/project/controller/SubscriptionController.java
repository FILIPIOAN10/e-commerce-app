package com.ecommerce.project.controller;

import com.ecommerce.project.payload.SubscriptionCheckoutDTO;
import com.ecommerce.project.payload.SubscriptionPlanDTO;
import com.ecommerce.project.payload.UserSubscriptionDTO;
import com.ecommerce.project.service.SubscriptionService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AuthUtil authUtil;

    @Tag(name = "Subscriptions")
    @GetMapping("/public/subscriptions/plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getActivePlans() {
        return ResponseEntity.ok(subscriptionService.getActivePlans());
    }

    @Tag(name = "Subscriptions")
    @GetMapping("/public/subscriptions/plans/{planId}")
    public ResponseEntity<SubscriptionPlanDTO> getPlanById(@PathVariable Long planId) {
        return ResponseEntity.ok(subscriptionService.getPlanById(planId));
    }

    @Tag(name = "Subscriptions")
    @PostMapping("/subscriptions/plans/{planId}/checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionCheckoutDTO> createCheckoutSession(@PathVariable Long planId) {
        String email = authUtil.loggedInEmail();
        return new ResponseEntity<>(subscriptionService.createCheckoutSession(planId, email), HttpStatus.CREATED);
    }

    @Tag(name = "Subscriptions")
    @GetMapping("/subscriptions/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserSubscriptionDTO>> getMySubscriptions() {
        String email = authUtil.loggedInEmail();
        return ResponseEntity.ok(subscriptionService.getMySubscriptions(email));
    }

    @Tag(name = "Subscriptions")
    @PostMapping("/subscriptions/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserSubscriptionDTO> cancelSubscription(@PathVariable Long id) {
        String email = authUtil.loggedInEmail();
        return ResponseEntity.ok(subscriptionService.cancelSubscription(id, email));
    }

    // Subscription webhooks now arrive at the single receiver
    // (POST /api/public/webhooks/stripe) — it verifies the signature and
    // de-duplicates on the Stripe event id before routing to a
    // SubscriptionEventHandler. Point every Stripe event there.

    @Tag(name = "Admin Subscriptions")
    @GetMapping("/admin/subscriptions/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        return ResponseEntity.ok(subscriptionService.getAllPlans());
    }

    @Tag(name = "Admin Subscriptions")
    @PostMapping("/admin/subscriptions/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlanDTO> createPlan(@Valid @RequestBody SubscriptionPlanDTO planDTO) {
        return new ResponseEntity<>(subscriptionService.createPlan(planDTO), HttpStatus.CREATED);
    }

    @Tag(name = "Admin Subscriptions")
    @PutMapping("/admin/subscriptions/plans/{planId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlanDTO> updatePlan(@PathVariable Long planId,
                                                          @Valid @RequestBody SubscriptionPlanDTO planDTO) {
        return ResponseEntity.ok(subscriptionService.updatePlan(planId, planDTO));
    }

    @Tag(name = "Admin Subscriptions")
    @DeleteMapping("/admin/subscriptions/plans/{planId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePlan(@PathVariable Long planId) {
        subscriptionService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }
}
