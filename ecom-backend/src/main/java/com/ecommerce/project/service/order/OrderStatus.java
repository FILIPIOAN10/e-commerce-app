package com.ecommerce.project.service.order;

import com.ecommerce.project.exception.APIException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explicit order status state machine.
 * <p>
 * Previously any status could transition to any other status, which allowed
 * nonsensical flows such as {@code Delivered -> Placed} or reviving a refunded
 * order. Transitions are now validated against an allow-list.
 */
public final class OrderStatus {

    public static final String PLACED = "Placed";
    public static final String PACKED = "Packed";
    public static final String SHIPPED = "Shipped";
    public static final String DELIVERED = "Delivered";
    public static final String CANCELLED = "Cancelled";
    public static final String RETURN_REQUESTED = "Return Requested";
    public static final String RETURNED = "Returned";
    public static final String REFUNDED = "Refunded";

    public static final List<String> ALL = List.of(
            PLACED, PACKED, SHIPPED, DELIVERED, CANCELLED,
            RETURN_REQUESTED, RETURNED, REFUNDED
    );

    /** Statuses in which the stock is still committed to the customer. */
    private static final Set<String> STOCK_COMMITTED = Set.of(
            PLACED, PACKED, SHIPPED, DELIVERED, RETURN_REQUESTED
    );

    /** Statuses that release stock back to inventory when entered. */
    private static final Set<String> STOCK_RELEASING = Set.of(
            CANCELLED, RETURNED
    );

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            PLACED, Set.of(PACKED, SHIPPED, CANCELLED),
            PACKED, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(DELIVERED, CANCELLED),
            DELIVERED, Set.of(RETURN_REQUESTED),
            RETURN_REQUESTED, Set.of(RETURNED, DELIVERED),
            RETURNED, Set.of(REFUNDED),
            REFUNDED, Set.of(),
            CANCELLED, Set.of(REFUNDED)
    );

    private OrderStatus() {
        // utility class
    }

    public static boolean isValid(String status) {
        return status != null && ALL.contains(status);
    }

    public static void assertTransitionAllowed(String from, String to) {
        if (!isValid(to)) {
            throw new APIException("Invalid order status: " + to + ". Valid statuses: " + ALL);
        }
        if (from == null) {
            return;
        }
        if (from.equals(to)) {
            throw new APIException("Order is already in status: " + to);
        }
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new APIException("Cannot change order status from '" + from + "' to '" + to
                    + "'. Allowed next statuses: " + (allowed.isEmpty() ? "none" : allowed));
        }
    }

    /**
     * True when moving from {@code from} to {@code to} must return the reserved
     * stock to inventory.
     */
    public static boolean releasesStock(String from, String to) {
        return STOCK_RELEASING.contains(to) && STOCK_COMMITTED.contains(from);
    }
}
