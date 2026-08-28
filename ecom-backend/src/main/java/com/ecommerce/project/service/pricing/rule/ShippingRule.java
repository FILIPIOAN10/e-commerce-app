package com.ecommerce.project.service.pricing.rule;

import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PriceLineType;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.PricingRule;
import com.ecommerce.project.service.pricing.ShippingCalculator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Adds the shipping charge, computed by {@link ShippingCalculator} from the
 * post-discount running total (so the free-shipping threshold is checked against
 * what the customer actually pays). Runs after {@link CouponDiscountRule}.
 */
@Component
@Order(20)
public class ShippingRule implements PricingRule {

    private final ShippingCalculator shippingCalculator;

    public ShippingRule(ShippingCalculator shippingCalculator) {
        this.shippingCalculator = shippingCalculator;
    }

    @Override
    public void apply(PricingContext context, PriceBreakdown breakdown) {
        Money shipping = shippingCalculator.calculate(context.address(), breakdown.runningTotal());
        breakdown.addCharge(PriceLineType.SHIPPING, "Shipping", shipping);
    }
}
