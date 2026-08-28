package com.ecommerce.project.service.search;

import java.math.BigDecimal;
import java.util.List;

/**
 * The filters a faceted product search can carry. Every field is optional; a
 * {@code null} means "this dimension is not constrained", which is what lets one
 * filter object describe both the full result set and each drill-down.
 *
 * @param keyword     free text, matched the same way the catalogue listing matches it
 * @param categoryIds restrict to these categories (OR within the dimension)
 * @param minPrice    inclusive lower bound on the price actually charged
 * @param maxPrice    inclusive upper bound on the price actually charged
 * @param minRating   inclusive lower bound on the denormalised average rating
 * @param inStock     {@code TRUE} for in-stock only; {@code null} for either
 */
public record ProductFilter(
        String keyword,
        List<Long> categoryIds,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minRating,
        Boolean inStock) {

    public ProductFilter {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
    }

    public static ProductFilter empty() {
        return new ProductFilter(null, List.of(), null, null, null, null);
    }

    public boolean hasCategories() {
        return !categoryIds.isEmpty();
    }
}
