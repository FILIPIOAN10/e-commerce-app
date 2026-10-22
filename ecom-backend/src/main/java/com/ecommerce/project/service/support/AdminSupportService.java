package com.ecommerce.project.service.support;

import com.ecommerce.project.exception.SupportPilotException;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.support.SupportDtos.Conversation;
import com.ecommerce.project.payload.support.SupportDtos.PendingRefund;
import com.ecommerce.project.repository.SupportConversationRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.support.SupportPilotApi.ActionOut;
import com.ecommerce.project.service.support.SupportPilotApi.ApprovalOut;
import com.ecommerce.project.service.support.SupportPilotApi.ChatOut;
import com.ecommerce.project.service.support.SupportPilotApi.OrderView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The admin's side: the queue of refunds the agent paused on, and the decision
 * that resumes it. SupportPilot owns the queue (a paused LangGraph run cannot
 * live anywhere else); this class adds the shop's view of each row — the
 * customer's email and the order's real total — so the admin decides on facts.
 */
@Service
@RequiredArgsConstructor
public class AdminSupportService {

    private static final String REFUND_TOOL = "request_refund";

    private final SupportPilotClient client;
    private final SupportOrderService supportOrderService;
    private final UserRepository userRepository;
    private final SupportConversationRepository conversations;

    /** Bounded by how many refunds are waiting at once — a handful — so one lookup per row is fine. */
    public List<PendingRefund> pendingRefunds() {
        return client.pendingApprovals().stream()
                .map(this::toPendingRefund)
                .toList();
    }

    /**
     * Approve or reject. On approve SupportPilot resumes the paused run, its
     * {@code request_refund} tool calls {@code /internal/support/orders/{id}/refunds},
     * and the return appears in Admin → Returns already APPROVED.
     */
    public ChatOut decide(String conversationId, String decision, String note, String adminUsername) {
        ChatOut out = client.decide(conversationId, decision, note, adminUsername);
        conversations.findById(conversationId).ifPresent(c -> {
            c.setStatus(out.status());
            conversations.save(c);
        });
        return out;
    }

    public Conversation transcript(String conversationId) {
        Long userId = conversations.findById(conversationId)
                .orElseThrow(() -> new SupportPilotException(HttpStatus.NOT_FOUND, "No such conversation."))
                .getUserId();
        return SupportChatService.toConversation(
                client.conversation(SupportCustomerIds.of(userId), conversationId));
    }

    private PendingRefund toPendingRefund(ApprovalOut approval) {
        Optional<User> user = SupportCustomerIds.userIdOf(approval.customerId()).flatMap(userRepository::findById);
        Map<String, Object> args = approval.actions().stream()
                .filter(a -> REFUND_TOOL.equals(a.name()))
                .findFirst()
                .map(ActionOut::args)
                .orElse(Map.of());
        String orderId = String.valueOf(args.getOrDefault("order_id", ""));
        Optional<OrderView> order = supportOrderService.findOrder(orderId);
        return new PendingRefund(
                approval.conversationId(),
                approval.requestedAt(),
                user.map(User::getUserId).orElse(null),
                user.map(User::getEmail).orElse(null),
                user.map(User::getUserName).orElse(null),
                orderId,
                order.map(OrderView::status).orElse("not found"),
                order.map(OrderView::total).orElse(null),
                order.map(OrderView::currency).orElse(null),
                String.valueOf(args.getOrDefault("reason", "")));
    }
}
