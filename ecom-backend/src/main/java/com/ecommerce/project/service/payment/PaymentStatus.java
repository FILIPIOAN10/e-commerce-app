package com.ecommerce.project.service.payment;

import com.ecommerce.project.service.order.OrderStatus;

import java.util.Map;
import java.util.Optional;

/**
 * Payment gateway status, kept deliberately separate from {@link OrderStatus}.
 * <p>
 * Both used to be plain {@code String}s, which allowed a payment status to be
 * compared against an order status. {@code OrderStatus.REFUNDED.equals("refunded")}
 * compiled happily and was always {@code false}, so refunds silently never
 * transitioned the order. Modelling them as distinct types makes that class of
 * bug impossible to express.
 */
public enum PaymentStatus {

    SUCCEEDED("succeeded"),
    FAILED("failed"),
    REFUNDED("refunded");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    /** The value persisted in {@code payment.pg_status}. */
    public String value() {
        return value;
    }

    /**
     * The order status a payment status forces, when it forces one at all.
     * A successful payment does not move the order: the order is already
     * {@code Placed} and progresses through the fulfilment flow instead.
     */
    private static final Map<PaymentStatus, String> ORDER_STATUS = Map.of(
            FAILED, OrderStatus.CANCELLED,
            REFUNDED, OrderStatus.REFUNDED
    );

    public Optional<String> requiredOrderStatus() {
        return Optional.ofNullable(ORDER_STATUS.get(this));
    }
}
