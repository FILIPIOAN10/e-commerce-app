package com.ecommerce.project.payload.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** The shop's own support API, as the React app sees it (camelCase, no SupportPilot internals). */
public final class SupportDtos {

    private SupportDtos() {
    }

    public record SupportStatus(boolean enabled) {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 64) String conversationId) {
    }

    /** status: open | awaiting_approval. While awaiting_approval the widget polls the conversation. */
    public record ChatResponse(String conversationId, String status, String reply) {
    }

    public record Message(String role, String content) {
    }

    public record Conversation(String conversationId, String status, List<Message> messages) {
    }

    /**
     * One refund the agent wants to file, enriched with what the admin needs to
     * decide: who, which order, how much. {@code orderTotal} is in {@code currency}.
     */
    public record PendingRefund(
            String conversationId,
            OffsetDateTime requestedAt,
            Long userId,
            String customerEmail,
            String customerName,
            String orderId,
            String orderStatus,
            BigDecimal orderTotal,
            String currency,
            String reason) {
    }

    public record Decision(
            @NotNull @Pattern(regexp = "approve|reject") String decision,
            @Size(max = 500) String note) {
    }
}
