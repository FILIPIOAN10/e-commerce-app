package com.ecommerce.project.payload;

/**
 * One clickable option in a facet, with the number of results it would yield.
 *
 * @param value the value to send back as a filter parameter
 * @param label what to show the customer
 * @param count how many products match if this option is applied on top of the
 *              other active filters
 */
public record FacetBucket(String value, String label, long count) {
}
