package com.ecommerce.project.service.search;

/**
 * The dimensions a product search can be faceted on.
 *
 * <p>Naming them is what makes drill-down counts possible: each dimension's
 * counts are computed with every filter applied <em>except its own</em>, so the
 * numbers next to "Books" answer "how many results would I get if I clicked
 * this", not "how many results does my current selection already contain".
 */
public enum ProductFacetDimension {
    CATEGORY,
    PRICE,
    RATING,
    AVAILABILITY
}
