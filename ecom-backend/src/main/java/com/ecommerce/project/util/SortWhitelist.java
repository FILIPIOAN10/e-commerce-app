package com.ecommerce.project.util;

import com.ecommerce.project.exception.APIException;

import java.util.Set;

/**
 * Whitelist of sortable properties per entity.
 * <p>
 * {@code Sort.by(userInput)} is not SQL injection (Spring Data resolves property
 * names), but an unvalidated value still allows two problems:
 * <ul>
 *   <li>an unknown property produces a {@code PropertyReferenceException} → 500;</li>
 *   <li>a nested association path such as {@code user.password} leaks ordering
 *       information about sensitive columns.</li>
 * </ul>
 * Only the properties listed here may be used for sorting.
 */
public final class SortWhitelist {

    public static final Set<String> PRODUCT = Set.of(
            "productId", "productName", "price", "specialPrice",
            "discount", "quantity", "averageRating"
    );

    public static final Set<String> ORDER = Set.of(
            "id", "orderDate", "totalAmount", "orderStatus", "email"
    );

    public static final Set<String> CATEGORY = Set.of(
            "categoryId", "categoryName"
    );

    public static final Set<String> USER = Set.of(
            "userId", "userName", "email"
    );

    private SortWhitelist() {
        // utility class
    }

    /**
     * Returns {@code sortBy} when it is allowed, otherwise falls back to
     * {@code defaultValue}. Rejects clearly hostile input with a 400.
     */
    public static String sanitize(String sortBy, Set<String> allowed, String defaultValue) {
        if (sortBy == null || sortBy.isBlank()) {
            return defaultValue;
        }
        String candidate = sortBy.trim();
        if (allowed.contains(candidate)) {
            return candidate;
        }
        throw new APIException("Invalid sort property '" + candidate
                + "'. Allowed values: " + allowed.stream().sorted().toList());
    }
}
