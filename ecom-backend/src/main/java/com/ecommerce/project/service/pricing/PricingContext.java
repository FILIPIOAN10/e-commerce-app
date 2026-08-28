package com.ecommerce.project.service.pricing;

import com.ecommerce.project.model.Address;

import java.util.List;

/**
 * The inputs a {@link PricingRule} needs. Immutable: rules read from here and
 * write only to the {@link PriceBreakdown}.
 *
 * @param subtotal     the pre-adjustment total of the cart / order lines
 * @param address      delivery address (shipping rules need it); may be {@code null}
 * @param couponCodes  coupon codes entered at checkout; may be {@code null} or empty
 */
public record PricingContext(double subtotal, Address address, List<String> couponCodes) {

    public static PricingContext of(double subtotal, Address address, List<String> couponCodes) {
        return new PricingContext(subtotal, address, couponCodes);
    }
}
