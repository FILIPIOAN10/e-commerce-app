package com.ecommerce.project.service.order;

import com.ecommerce.project.exception.APIException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ecommerce.project.service.order.OrderStatus.CANCELLED;
import static com.ecommerce.project.service.order.OrderStatus.DELIVERED;
import static com.ecommerce.project.service.order.OrderStatus.PACKED;
import static com.ecommerce.project.service.order.OrderStatus.PLACED;
import static com.ecommerce.project.service.order.OrderStatus.REFUNDED;
import static com.ecommerce.project.service.order.OrderStatus.RETURNED;
import static com.ecommerce.project.service.order.OrderStatus.RETURN_REQUESTED;
import static com.ecommerce.project.service.order.OrderStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderStatus state machine")
class OrderStatusTest {

    @Test
    @DisplayName("permitted transitions are accepted")
    void allowsDeclaredTransitions() {
        assertThatCode(() -> {
            OrderStatus.assertTransitionAllowed(PLACED, PACKED);
            OrderStatus.assertTransitionAllowed(PACKED, SHIPPED);
            OrderStatus.assertTransitionAllowed(SHIPPED, DELIVERED);
            OrderStatus.assertTransitionAllowed(DELIVERED, RETURN_REQUESTED);
            OrderStatus.assertTransitionAllowed(RETURN_REQUESTED, RETURNED);
            OrderStatus.assertTransitionAllowed(RETURNED, REFUNDED);
            OrderStatus.assertTransitionAllowed(SHIPPED, CANCELLED);
            OrderStatus.assertTransitionAllowed(CANCELLED, REFUNDED);
            OrderStatus.assertTransitionAllowed(null, PLACED); // initial assignment
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nonsensical transitions are rejected")
    void rejectsUndeclaredTransitions() {
        assertThatThrownBy(() -> OrderStatus.assertTransitionAllowed(DELIVERED, PLACED))
                .isInstanceOf(APIException.class);
        assertThatThrownBy(() -> OrderStatus.assertTransitionAllowed(REFUNDED, PLACED))
                .isInstanceOf(APIException.class);
        assertThatThrownBy(() -> OrderStatus.assertTransitionAllowed(CANCELLED, SHIPPED))
                .isInstanceOf(APIException.class);
        assertThatThrownBy(() -> OrderStatus.assertTransitionAllowed(PLACED, PLACED))
                .isInstanceOf(APIException.class);
        assertThatThrownBy(() -> OrderStatus.assertTransitionAllowed(PLACED, "Teleported"))
                .isInstanceOf(APIException.class);
    }

    @Test
    @DisplayName("only committed -> releasing transitions return stock")
    void releasesStockOnlyWhenLeavingAHoldingState() {
        assertThat(OrderStatus.releasesStock(PLACED, CANCELLED)).isTrue();
        assertThat(OrderStatus.releasesStock(SHIPPED, CANCELLED)).isTrue();
        assertThat(OrderStatus.releasesStock(RETURN_REQUESTED, RETURNED)).isTrue();

        assertThat(OrderStatus.releasesStock(CANCELLED, REFUNDED)).isFalse(); // already released
        assertThat(OrderStatus.releasesStock(PLACED, SHIPPED)).isFalse();     // still committed
        assertThat(OrderStatus.releasesStock(null, CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("ALL lists every status, isValid guards membership")
    void allAndIsValid() {
        assertThat(OrderStatus.ALL).containsExactly(
                PLACED, PACKED, SHIPPED, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED, REFUNDED);
        assertThat(OrderStatus.isValid(SHIPPED)).isTrue();
        assertThat(OrderStatus.isValid("nope")).isFalse();
        assertThat(OrderStatus.isValid(null)).isFalse();
    }
}
