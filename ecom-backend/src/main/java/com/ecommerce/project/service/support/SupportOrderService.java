package com.ecommerce.project.service.support;

import com.ecommerce.project.config.SupportPilotProperties;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ReturnRequestDTO;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ReturnRequestRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.ReturnService;
import com.ecommerce.project.service.support.SupportPilotApi.OrderView;
import com.ecommerce.project.service.support.SupportPilotApi.RefundOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What SupportPilot's tools see of the shop: one order, and one way to act on it.
 *
 * <p>SupportPilot's {@code request_refund} tool runs only after an admin approved
 * it in the AI queue. Here it becomes an ordinary return, created and approved
 * through {@link ReturnService} so every existing rule (own order, Delivered,
 * no open return) still applies. Money is not touched: it moves later, when an
 * admin marks the returned parcel refunded and the outbox calls Stripe.
 *
 * <p>The shop re-checks everything the agent already checked. SupportPilot is a
 * client; the rules that guard money live with the money.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportOrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnService returnService;
    private final SupportPilotProperties properties;

    @Transactional(readOnly = true)
    public Optional<OrderView> findOrder(String rawOrderId) {
        return parseOrderId(rawOrderId)
                .flatMap(orderRepository::findByIdWithDetails)
                .map(this::toView);
    }

    /**
     * Files and approves a return for an order the agent's customer owns.
     * Idempotent on {@code idempotencyKey}: the same key returns the same return.
     *
     * @throws ResourceNotFoundException the order does not exist or is not this customer's (same answer for both)
     * @throws APIException              the order cannot be returned (status, window, an open return)
     */
    @Transactional
    public RefundOut createApprovedReturn(String rawOrderId, String customerId, String reason,
                                          String idempotencyKey, String conversationId, String approvedBy) {
        Optional<ReturnRequest> replay = returnRequestRepository.findBySupportIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return refundOut(replay.get());
        }

        Order order = parseOrderId(rawOrderId)
                .flatMap(orderRepository::findById)
                .filter(o -> ownedBy(o, customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", rawOrderId));

        assertInsideWindow(order);

        // requestReturn re-checks ownership, Delivered and "no open return".
        ReturnRequestDTO filed = returnService.requestReturn(order.getId(), order.getEmail(), truncate(reason, 500));

        ReturnRequest created = returnRequestRepository.findById(filed.getId()).orElseThrow();
        created.setSource(ReturnRequest.SOURCE_SUPPORTPILOT);
        created.setSupportConversationId(conversationId);
        created.setSupportIdempotencyKey(idempotencyKey);
        returnRequestRepository.save(created);

        returnService.approveReturn(created.getId(),
                truncate("Approved by " + approvedBy + " in the AI support queue (conversation " + conversationId + ")", 255));

        log.info("support return created return={} order={} conversation={} approvedBy={}",
                created.getId(), order.getId(), conversationId, approvedBy);
        return refundOut(created);
    }

    private void assertInsideWindow(Order order) {
        Instant deliveredAt = order.getDeliveredAt();
        Duration window = Duration.ofDays(properties.getRefundWindowDays());
        if (deliveredAt == null || Instant.now().isAfter(deliveredAt.plus(window))) {
            throw new APIException("The order was delivered more than " + properties.getRefundWindowDays()
                    + " days ago, or has not been delivered");
        }
    }

    private boolean ownedBy(Order order, String customerId) {
        return SupportCustomerIds.userIdOf(customerId)
                .flatMap(userRepository::findById)
                .map(User::getEmail)
                .filter(email -> email.equals(order.getEmail()))
                .isPresent();
    }

    private OrderView toView(Order order) {
        // Orders are keyed by email; a guest order with no account maps to no
        // customer, and SupportPilot's ownership check then treats it as not found.
        String customerId = userRepository.findByEmail(order.getEmail())
                .map(u -> SupportCustomerIds.of(u.getUserId()))
                .orElse(null);
        List<String> items = order.getOrderItems().stream()
                .map(OrderItem::getProduct)
                .filter(Objects::nonNull)
                .map(Product::getProductName)
                .toList();
        // Stored in USD; shown in the currency the customer paid in, at the rate they were quoted.
        BigDecimal total = order.getTotalAmount() == null ? null
                : order.getTotalAmount().multiply(order.getExchangeRate()).setScale(2, RoundingMode.HALF_UP);
        return new OrderView(
                String.valueOf(order.getId()),
                customerId,
                agentStatus(order.getOrderStatus()),
                items,
                total,
                order.getCurrencyCode(),
                null, // outbound shipments have no carrier/ETA in this shop yet
                null,
                order.getOrderDate() == null ? null : order.getOrderDate().toString(),
                order.getDeliveredAt() == null ? null : order.getDeliveredAt().atOffset(ZoneOffset.UTC).toString());
    }

    /** "Return Requested" -> "return_requested": SupportPilot's tools compare with lower-case "delivered". */
    static String agentStatus(String shopStatus) {
        if (shopStatus == null) {
            return "unknown";
        }
        return shopStatus.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static RefundOut refundOut(ReturnRequest r) {
        // Not "refunded": the money moves after the parcel is back. The status word is what the agent repeats.
        return new RefundOut("RR-" + r.getId(), "return_approved_refund_after_parcel_arrives");
    }

    private static Optional<Long> parseOrderId(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.trim().replaceFirst("^#", "");
        if (!digits.matches("\\d{1,18}")) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(digits));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return "No reason provided";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
