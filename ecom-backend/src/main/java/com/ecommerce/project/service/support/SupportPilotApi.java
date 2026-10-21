package com.ecommerce.project.service.support;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The JSON both sides of the SupportPilot boundary speak, in SupportPilot's own
 * snake_case. Kept in one place so a contract change is one diff, and kept apart
 * from the shop's camelCase API in {@code payload.support}, which the browser sees.
 */
public final class SupportPilotApi {

    private SupportPilotApi() {
    }

    // --- shop -> SupportPilot (/v1/...) ---------------------------------------------

    public record ChatIn(String message, @JsonProperty("conversation_id") String conversationId) {
    }

    /** status is "open" or "awaiting_approval". */
    public record ChatOut(@JsonProperty("conversation_id") String conversationId, String status, String reply) {
    }

    /** role is "customer" or "assistant"; tool calls never leave SupportPilot. */
    public record MessageOut(String role, String content) {
    }

    public record ConversationOut(@JsonProperty("conversation_id") String conversationId,
                                  String status,
                                  List<MessageOut> messages) {
    }

    /** One gated tool call waiting for a person: {name: "request_refund", args: {order_id, reason}}. */
    public record ActionOut(String name, Map<String, Object> args) {
    }

    public record ApprovalOut(@JsonProperty("conversation_id") String conversationId,
                              @JsonProperty("customer_id") String customerId,
                              @JsonProperty("requested_at") OffsetDateTime requestedAt,
                              List<ActionOut> actions) {
    }

    public record DecisionIn(String decision, String note) {
    }

    // --- SupportPilot -> shop (/internal/support/...) -------------------------------

    /**
     * An order as SupportPilot's tools expect it (its {@code orders_mock.py} is the
     * reference). {@code customer_id} is what the tool compares with the customer
     * of the conversation; dates are ISO-8601 strings because Python parses them
     * with {@code datetime.fromisoformat}.
     */
    public record OrderView(String id,
                            @JsonProperty("customer_id") String customerId,
                            String status,
                            List<String> items,
                            BigDecimal total,
                            String currency,
                            String carrier,
                            String eta,
                            @JsonProperty("placed_at") String placedAt,
                            @JsonProperty("delivered_at") String deliveredAt) {
    }

    public record RefundIn(String reason) {
    }

    public record RefundOut(@JsonProperty("refund_id") String refundId, String status) {
    }
}
