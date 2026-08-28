package com.ecommerce.project.payload;

import java.util.List;

/**
 * The counts beside each filter option, one list per dimension.
 *
 * <p>Every count is a drill-down count: it already accounts for the other active
 * filters, so clicking an option yields exactly the number shown. A zero-count
 * option is still returned rather than hidden — a filter that silently vanishes
 * is harder to reason about than one that is visibly empty.
 */
public record ProductFacets(
        List<FacetBucket> categories,
        List<FacetBucket> priceRanges,
        List<FacetBucket> ratings,
        List<FacetBucket> availability) {
}
