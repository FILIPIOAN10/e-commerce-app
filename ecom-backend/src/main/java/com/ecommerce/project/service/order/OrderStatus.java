package com.ecommerce.project.service.order;

import com.ecommerce.project.exception.APIException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Explicit order-status state machine.
 * <p>
 * Previously any status could transition to any other, which allowed nonsensical
 * flows such as {@code Delivered -> Placed} or reviving a refunded order.
 * <p>
 * The graph now lives on {@link Node}: each status declares its label, whether
 * entering it releases reserved stock, and its permitted successors, all on one
 * line — so the whole machine reads top-to-bottom. The {@code String} constants
 * and static helpers below are the public surface (order statuses are stored and
 * carried as plain strings).
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

    private enum StockEffect { COMMITTED, RELEASING, NEUTRAL }

    /**
     * The state machine. Read each line as: {@code STATE(label, stock effect on
     * entry, allowed next states...)}.
     */
    private enum Node {
        S_PLACED           (PLACED,           StockEffect.COMMITTED, PACKED, SHIPPED, CANCELLED),
        S_PACKED           (PACKED,           StockEffect.COMMITTED, SHIPPED, CANCELLED),
        S_SHIPPED          (SHIPPED,          StockEffect.COMMITTED, DELIVERED, CANCELLED),
        S_DELIVERED        (DELIVERED,        StockEffect.COMMITTED, RETURN_REQUESTED),
        S_CANCELLED        (CANCELLED,        StockEffect.RELEASING, REFUNDED),
        S_RETURN_REQUESTED (RETURN_REQUESTED, StockEffect.COMMITTED, RETURNED, DELIVERED),
        S_RETURNED         (RETURNED,         StockEffect.RELEASING, REFUNDED),
        S_REFUNDED         (REFUNDED,         StockEffect.NEUTRAL);

        final String label;
        final StockEffect stockEffect;
        final Set<String> allowedNext;

        Node(String label, StockEffect stockEffect, String... allowedNext) {
            this.label = label;
            this.stockEffect = stockEffect;
            this.allowedNext = Set.of(allowedNext);
        }

        static Optional<Node> of(String label) {
            return Arrays.stream(values()).filter(n -> n.label.equals(label)).findFirst();
        }
    }

    public static final List<String> ALL = Arrays.stream(Node.values()).map(n -> n.label).toList();

    private OrderStatus() {
    }

    public static boolean isValid(String status) {
        return status != null && Node.of(status).isPresent();
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
        Set<String> allowed = Node.of(from).map(n -> n.allowedNext).orElse(Set.of());
        if (!allowed.contains(to)) {
            throw new APIException("Cannot change order status from '" + from + "' to '" + to
                    + "'. Allowed next statuses: " + (allowed.isEmpty() ? "none" : allowed));
        }
    }

    /**
     * True when moving from {@code from} to {@code to} must return the reserved
     * stock to inventory: the order currently holds stock and the target status
     * releases it.
     */
    public static boolean releasesStock(String from, String to) {
        boolean wasHolding = Node.of(from).map(n -> n.stockEffect == StockEffect.COMMITTED).orElse(false);
        boolean nowReleasing = Node.of(to).map(n -> n.stockEffect == StockEffect.RELEASING).orElse(false);
        return wasHolding && nowReleasing;
    }
}
