package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One SupportPilot conversation, as the shop knows it: whose it is and whether
 * it is paused for an admin. The messages themselves stay in SupportPilot (the
 * LangGraph checkpoint is the transcript), so this row holds no chat text.
 */
@Entity
@Table(name = "support_conversations")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class SupportConversation {

    @Id
    @Column(name = "conversation_id", length = 64)
    @EqualsAndHashCode.Include
    @ToString.Include
    private String conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** open | awaiting_approval — SupportPilot's own two states. */
    @Column(nullable = false, length = 20)
    @ToString.Include
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SupportConversation(String conversationId, Long userId, String status) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.status = status;
    }
}
