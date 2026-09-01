package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Record of a completed (or in-flight) idempotent request. The unique
 * {@code (idempotency_key, scope)} constraint is what makes claiming a key
 * atomic under concurrency.
 */
@Entity
@Table(name = "idempotency_keys",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_idempotency_key_scope", columnNames = {"idempotency_key", "scope"}))
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    /** Which operation this key belongs to, so the same key on two endpoints never collides. */
    @Column(nullable = false, length = 100)
    private String scope;

    /** SHA-256 of the request, to reject a key reused for a different payload. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "response_status")
    private Integer responseStatus;

    // No @Lob: on Postgres, @Lob on a String binds the column as a Large Object (oid),
    // which requires an active transaction. The idempotency service runs non-transactionally
    // (auto-commit), so @Lob throws "Large Objects may not be used in auto-commit mode".
    // A plain TEXT column stores the response body fine without a transaction.
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public IdempotencyKey(String idempotencyKey, String scope, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.scope = scope;
        this.requestHash = requestHash;
        this.status = Status.IN_PROGRESS;
    }

    public void complete(int responseStatus, String responseBody) {
        this.status = Status.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.completedAt = Instant.now();
    }
}
