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

    // The names below are JPA *property* names, not column names: Spring Data
    // resolves them against the entity, so `reviewId` (the column) is not
    // sortable — `id` (the field) is. Getting this wrong turns a working list
    // endpoint into a 400, so each set mirrors the entity it belongs to.

    /** {@link com.ecommerce.project.model.Review}. */
    public static final Set<String> REVIEW = Set.of(
            "id", "rating", "helpfulCount", "createdAt"
    );

    /** {@link com.ecommerce.project.model.ProductQuestion}. */
    public static final Set<String> QUESTION = Set.of(
            "id", "createdAt", "answeredAt"
    );

    /** {@link com.ecommerce.project.model.Wishlist}. */
    public static final Set<String> WISHLIST = Set.of(
            "id", "createdAt"
    );

    /** {@link com.ecommerce.project.model.PromoCampaign}. */
    public static final Set<String> PROMO_CAMPAIGN = Set.of(
            "id", "name", "startTime", "endTime", "discountPercent", "active"
    );

    /** {@link com.ecommerce.project.model.Address}. */
    public static final Set<String> ADDRESS = Set.of(
            "addressId", "city", "country", "pincode"
    );

    /** {@link com.ecommerce.project.model.Cart}. */
    public static final Set<String> CART = Set.of(
            "cartId", "totalPrice", "lastActivityAt"
    );

    /** {@link com.ecommerce.project.model.ReturnRequest}. */
    public static final Set<String> RETURN_REQUEST = Set.of(
            "id", "orderId", "userEmail", "status", "requestedAt", "processedAt"
    );

    /** {@link com.ecommerce.project.model.StockMovement}. */
    public static final Set<String> STOCK_MOVEMENT = Set.of(
            "id", "productId", "delta", "balanceAfter", "reason", "createdAt"
    );

    /** {@link com.ecommerce.project.model.UserActivityLog}. */
    public static final Set<String> ACTIVITY_LOG = Set.of(
            "id", "username", "action", "createdAt"
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