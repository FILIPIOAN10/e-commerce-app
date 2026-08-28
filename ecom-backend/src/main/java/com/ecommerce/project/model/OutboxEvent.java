package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;

/**
 * One durable, retryable side effect owed as a result of a committed transaction.
 * Written by {@link com.ecommerce.project.service.outbox.OutboxEventPublisher} in
 * the business transaction; drained by
 * {@link com.ecommerce.project.service.outbox.OutboxDispatcher}.
 */
@Entity
@Table(name = "outbox_event", indexes = {
        @Index(name = "idx_outbox_status_next_attempt", columnList = "status, next_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    /** Longest error string kept in {@link #lastError}. */
    private static final int MAX_ERROR_LEN = 4000;

    /** Retry backoff is never longer than this regardless of attempt count. */
    private static final long MAX_BACKOFF_SECONDS = 3600;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OutboxEvent of(String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.eventType = eventType;
        event.payload = payload;
        return event;
    }

    /** The side effect completed. */
    public void markDone() {
        this.status = OutboxStatus.DONE;
    }

    /**
     * A dispatch attempt failed. Increments the attempt count and either schedules
     * an exponentially backed-off retry or, once {@code maxAttempts} is reached,
     * dead-letters the event.
     */
    public void recordFailure(Throwable error, int maxAttempts, Duration baseBackoff) {
        this.attempts += 1;
        this.lastError = abbreviate(String.valueOf(error));
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.DEAD;
        } else {
            long backoff = Math.min(
                    baseBackoff.getSeconds() * (1L << (this.attempts - 1)),
                    MAX_BACKOFF_SECONDS);
            this.nextAttemptAt = Instant.now().plusSeconds(backoff);
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }
}
