package com.ecommerce.project.service.pricing;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the {@link PricingRule}s in order over a fresh {@link PriceBreakdown}.
 * <p>
 * Pure: no coupon usage is consumed and nothing is persisted, so it is safe to
 * call from {@code previewOrder} as well as checkout. Spring injects the rules
 * already sorted by {@code @Order}.
 */
@Component
public class PricingPipeline {

    private final List<PricingRule> rules;

    public PricingPipeline(List<PricingRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public PriceBreakdown price(PricingContext context) {
        PriceBreakdown breakdown = new PriceBreakdown(context.subtotal());
        for (PricingRule rule : rules) {
            rule.apply(context, breakdown);
        }
        return breakdown;
    }
}
