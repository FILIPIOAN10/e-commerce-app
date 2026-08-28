package com.ecommerce.project.service.pricing;

/**
 * Kind of a {@link PriceLine}. The order DTO reports discount and shipping
 * separately, so lines are summed by type; {@code TAX} is here for the VAT rule
 * that lands with the money-modelling work.
 */
public enum PriceLineType {
    DISCOUNT,
    SHIPPING,
    TAX
}
