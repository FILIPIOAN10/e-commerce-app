package com.ecommerce.project.service.pricing.rule;

import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PriceLineType;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.PricingRule;
import com.ecommerce.project.service.pricing.TaxRateResolver;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Adds the VAT line, last in the pipeline. The rate comes from
 * {@link TaxRateResolver} keyed on the delivery address, and the taxable base is
 * the running total <em>after</em> {@link CouponDiscountRule} and
 * {@link ShippingRule} have run — VAT is charged on what the customer actually
 * pays for the goods, which is the discounted price, and (in the EU) on the
 * carriage too. Whether shipping is in the base is
 * {@code app.tax.taxable-shipping}.
 *
 * <p>Runs at {@code @Order(30)}: after shipping (20), so the ordering that the
 * pipeline makes explicit now reads coupon &rarr; shipping &rarr; tax. A zero
 * rate — tax disabled, or a destination nothing charges — adds no line rather
 * than a &euro;0.00 one.
 */
@Component
@Order(30)
public class VatRule implements PricingRule {

    private final TaxRateResolver taxRateResolver;

    public VatRule(TaxRateResolver taxRateResolver) {
        this.taxRateResolver = taxRateResolver;
    }

    @Override
    public void apply(PricingContext context, PriceBreakdown breakdown) {
        BigDecimal ratePercent = taxRateResolver.ratePercentFor(context.address());
        if (ratePercent.signum() <= 0) {
            return;
        }

        Money taxableBase = taxRateResolver.taxableShipping()
                ? breakdown.runningTotal()
                : breakdown.runningTotal().subtract(breakdown.shippingTotal());

        Money tax = taxableBase.percentage(ratePercent.doubleValue());
        if (tax.isZero()) {
            return;
        }

        String label = "VAT (" + ratePercent.stripTrailingZeros().toPlainString() + "%)";
        breakdown.addCharge(PriceLineType.TAX, label, tax);
    }
}
