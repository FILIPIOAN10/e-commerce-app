package com.ecommerce.project.service.support;

import com.ecommerce.project.exception.SupportPilotException;
import com.ecommerce.project.model.SupportConversation;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.support.SupportDtos.ChatRequest;
import com.ecommerce.project.payload.support.SupportDtos.ChatResponse;
import com.ecommerce.project.payload.support.SupportDtos.Conversation;
import com.ecommerce.project.payload.support.SupportDtos.Message;
import com.ecommerce.project.repository.SupportConversationRepository;
import com.ecommerce.project.service.support.SupportPilotApi.ChatOut;
import com.ecommerce.project.service.support.SupportPilotApi.ConversationOut;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * The customer's side of the support chat. The shop is the gateway: it decides
 * who the customer is, checks the conversation is theirs, then forwards.
 *
 * <p>No {@code @Transactional} around the SupportPilot call on purpose: one chat
 * turn takes seconds, and a pooled DB connection must not wait on a model.
 */
@Service
@RequiredArgsConstructor
public class SupportChatService {

    private final SupportPilotClient client;
    private final SupportConversationRepository conversations;

    public boolean isEnabled() {
        return client.isEnabled();
    }

    public ChatResponse chat(User user, ChatRequest request) {
        String conversationId = blankToNull(request.conversationId());
        if (conversationId != null) {
            requireOwn(user, conversationId);
        }
        ChatOut out = client.chat(SupportCustomerIds.of(user.getUserId()), request.message().trim(), conversationId);
        remember(user.getUserId(), out.conversationId(), out.status());
        return new ChatResponse(out.conversationId(), out.status(), out.reply());
    }

    public Conversation conversation(User user, String conversationId) {
        requireOwn(user, conversationId);
        ConversationOut out = client.conversation(SupportCustomerIds.of(user.getUserId()), conversationId);
        // The status may have changed while the customer was away (an admin decided).
        remember(user.getUserId(), out.conversationId(), out.status());
        return toConversation(out);
    }

    /** The most recent conversation, so the widget resumes it on any device. */
    public Optional<Conversation> latest(User user) {
        return conversations.findFirstByUserIdOrderByUpdatedAtDesc(user.getUserId())
                .map(c -> conversation(user, c.getConversationId()));
    }

    static Conversation toConversation(ConversationOut out) {
        return new Conversation(out.conversationId(), out.status(),
                out.messages().stream().map(m -> new Message(m.role(), m.content())).toList());
    }

    private void requireOwn(User user, String conversationId) {
        // Someone else's conversation answers exactly like one that does not exist.
        conversations.findByConversationIdAndUserId(conversationId, user.getUserId())
                .orElseThrow(() -> new SupportPilotException(HttpStatus.NOT_FOUND, "No such conversation."));
    }

    private void remember(Long userId, String conversationId, String status) {
        SupportConversation row = conversations.findById(conversationId)
                .orElseGet(() -> new SupportConversation(conversationId, userId, status));
        row.setStatus(status);
        row.setUpdatedAt(Instant.now()); // dirty even when the status is unchanged, so "latest" means last message
        conversations.save(row);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
