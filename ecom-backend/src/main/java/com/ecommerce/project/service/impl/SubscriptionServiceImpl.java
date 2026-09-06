package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.SubscriptionPlan;
import com.ecommerce.project.model.UserSubscription;
import com.ecommerce.project.payload.SubscriptionCheckoutDTO;
import com.ecommerce.project.payload.SubscriptionPlanDTO;
import com.ecommerce.project.payload.UserSubscriptionDTO;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.SubscriptionPlanRepository;
import com.ecommerce.project.repository.UserSubscriptionRepository;
import com.ecommerce.project.service.SubscriptionService;
import com.ecommerce.project.service.pricing.Money;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final ProductRepository productRepository;

    @Value("${stripe.secret.key:}")
    private String stripeApiKey;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        if (stripeApiKey != null && !stripeApiKey.isBlank()) {
            Stripe.apiKey = stripeApiKey;
        }
    }

    private void ensureStripeConfigured() {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new APIException("Stripe API key is not configured");
        }
    }

    @Override
    @Transactional
    public SubscriptionPlanDTO createPlan(SubscriptionPlanDTO planDTO) {
        ensureStripeConfigured();

        Product product = productRepository.findById(planDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", planDTO.getProductId()));

        try {
            ProductCreateParams productParams = ProductCreateParams.builder()
                    .setName(planDTO.getName())
                    .setDescription(planDTO.getDescription())
                    .build();
            com.stripe.model.Product stripeProduct = com.stripe.model.Product.create(productParams);

            PriceCreateParams.Recurring.Interval interval =
                    "year".equalsIgnoreCase(planDTO.getInterval())
                            ? PriceCreateParams.Recurring.Interval.YEAR
                            : PriceCreateParams.Recurring.Interval.MONTH;

            PriceCreateParams priceParams = PriceCreateParams.builder()
                    .setProduct(stripeProduct.getId())
                    .setUnitAmount(Money.of(planDTO.getAmount()).toCents())
                    .setCurrency(planDTO.getCurrency().toLowerCase())
                    .setRecurring(PriceCreateParams.Recurring.builder().setInterval(interval).build())
                    .build();
            Price stripePrice = Price.create(priceParams);

            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setName(planDTO.getName());
            plan.setDescription(planDTO.getDescription());
            plan.setProduct(product);
            plan.setStripeProductId(stripeProduct.getId());
            plan.setStripePriceId(stripePrice.getId());
            plan.setInterval(planDTO.getInterval().toLowerCase());
            plan.setAmount(planDTO.getAmount());
            plan.setCurrency(planDTO.getCurrency().toUpperCase());
            plan.setActive(planDTO.getActive());

            return mapToDTO(planRepository.save(plan));
        } catch (StripeException e) {
            throw new APIException("Failed to create Stripe product/price: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public SubscriptionPlanDTO updatePlan(Long planId, SubscriptionPlanDTO planDTO) {
        ensureStripeConfigured();

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "planId", planId));

        Product product = productRepository.findById(planDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", planDTO.getProductId()));

        plan.setName(planDTO.getName());
        plan.setDescription(planDTO.getDescription());
        plan.setProduct(product);
        plan.setActive(planDTO.getActive());

        if (!plan.getInterval().equalsIgnoreCase(planDTO.getInterval()) ||
                plan.getAmount().compareTo(planDTO.getAmount()) != 0 ||
                !plan.getCurrency().equalsIgnoreCase(planDTO.getCurrency())) {
            try {
                PriceCreateParams.Recurring.Interval interval =
                        "year".equalsIgnoreCase(planDTO.getInterval())
                                ? PriceCreateParams.Recurring.Interval.YEAR
                                : PriceCreateParams.Recurring.Interval.MONTH;

                PriceCreateParams priceParams = PriceCreateParams.builder()
                        .setProduct(plan.getStripeProductId())
                        .setUnitAmount(Money.of(planDTO.getAmount()).toCents())
                        .setCurrency(planDTO.getCurrency().toLowerCase())
                        .setRecurring(PriceCreateParams.Recurring.builder().setInterval(interval).build())
                        .build();
                Price stripePrice = Price.create(priceParams);
                plan.setStripePriceId(stripePrice.getId());
                plan.setInterval(planDTO.getInterval().toLowerCase());
                plan.setAmount(planDTO.getAmount());
                plan.setCurrency(planDTO.getCurrency().toUpperCase());
            } catch (StripeException e) {
                throw new APIException("Failed to update Stripe price: " + e.getMessage());
            }
        }

        return mapToDTO(planRepository.save(plan));
    }

    @Override
    public SubscriptionPlanDTO getPlanById(Long planId) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "planId", planId));
        return mapToDTO(plan);
    }

    @Override
    public List<SubscriptionPlanDTO> getActivePlans() {
        return planRepository.findByActiveTrue().stream()
                .filter(SubscriptionPlan::getActive)
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<SubscriptionPlanDTO> getAllPlans() {
        return planRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deletePlan(Long planId) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "planId", planId));
        planRepository.delete(plan);
    }

    @Override
    @Transactional
    public SubscriptionCheckoutDTO createCheckoutSession(Long planId, String email) {
        ensureStripeConfigured();

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "planId", planId));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new APIException("Subscription plan is not active");
        }

        if (plan.getStripePriceId() == null || plan.getStripePriceId().isBlank()) {
            throw new APIException("Plan does not have a valid Stripe price");
        }

        try {
            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setEmail(email)
                    .build();
            Customer customer = Customer.create(customerParams);

            SessionCreateParams sessionParams = SessionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(frontendUrl + "/my-subscriptions?success=true&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(frontendUrl + "/subscriptions?canceled=true")
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(plan.getStripePriceId())
                                    .setQuantity(1L)
                                    .build()
                    )
                    .putMetadata("plan_id", plan.getPlanId().toString())
                    .putMetadata("email", email)
                    .build();

            Session session = Session.create(sessionParams);

            UserSubscription pending = new UserSubscription();
            pending.setEmail(email);
            pending.setPlan(plan);
            pending.setStripeCustomerId(customer.getId());
            pending.setStripeCheckoutSessionId(session.getId());
            pending.setStatus("PENDING");
            pending = userSubscriptionRepository.save(pending);

            return new SubscriptionCheckoutDTO(session.getUrl(), session.getId(), pending.getId());
        } catch (StripeException e) {
            throw new APIException("Failed to create checkout session: " + e.getMessage());
        }
    }

    @Override
    public List<UserSubscriptionDTO> getMySubscriptions(String email) {
        return userSubscriptionRepository.findByEmailOrderByCreatedAtDesc(email).stream()
                .map(this::mapUserSubscriptionToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserSubscriptionDTO cancelSubscription(Long id, String email) {
        UserSubscription subscription = userSubscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserSubscription", "id", id));

        if (!email.equals(subscription.getEmail())) {
            throw new APIException("You are not authorized to cancel this subscription");
        }

        if (subscription.getStripeSubscriptionId() != null && !subscription.getStripeSubscriptionId().isBlank()) {
            try {
                Subscription stripeSub = Subscription.retrieve(subscription.getStripeSubscriptionId());
                stripeSub.cancel(SubscriptionCancelParams.builder().build());
            } catch (StripeException e) {
                throw new APIException("Failed to cancel Stripe subscription: " + e.getMessage());
            }
        }

        subscription.setStatus("CANCELED");
        subscription.setCanceledAt(LocalDateTime.now());
        return mapUserSubscriptionToDTO(userSubscriptionRepository.save(subscription));
    }

    private SubscriptionPlanDTO mapToDTO(SubscriptionPlan plan) {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setPlanId(plan.getPlanId());
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        dto.setProductId(plan.getProduct() != null ? plan.getProduct().getProductId() : null);
        dto.setStripeProductId(plan.getStripeProductId());
        dto.setStripePriceId(plan.getStripePriceId());
        dto.setInterval(plan.getInterval());
        dto.setAmount(plan.getAmount());
        dto.setCurrency(plan.getCurrency());
        dto.setActive(plan.getActive());
        return dto;
    }

    private UserSubscriptionDTO mapUserSubscriptionToDTO(UserSubscription sub) {
        UserSubscriptionDTO dto = new UserSubscriptionDTO();
        dto.setId(sub.getId());
        dto.setEmail(sub.getEmail());
        dto.setPlan(mapToDTO(sub.getPlan()));
        dto.setStripeCheckoutSessionId(sub.getStripeCheckoutSessionId());
        dto.setStripeSubscriptionId(sub.getStripeSubscriptionId());
        dto.setStripeCustomerId(sub.getStripeCustomerId());
        dto.setStatus(sub.getStatus());
        dto.setCurrentPeriodStart(sub.getCurrentPeriodStart());
        dto.setCurrentPeriodEnd(sub.getCurrentPeriodEnd());
        dto.setCreatedAt(sub.getCreatedAt());
        dto.setCanceledAt(sub.getCanceledAt());
        return dto;
    }
}
