package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One Art. 15 data export: the request, and once built, the ZIP archive itself.
 *
 * <p>The archive is deliberately short-lived — {@link #expiresAt} is set when the
 * request is accepted and a scheduled purge drops {@link #payload} once it
 * passes. A copy of someone's entire personal data is exactly the thing not to
 * keep lying around longer than the user needs to fetch it.
 */
@Entity
@Table(name = "gdpr_export", indexes = {
        @Index(name = "idx_gdpr_export_user_status", columnList = "user_id, status"),
        @Index(name = "idx_gdpr_export_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class GdprExport {

    /** Longest error string kept in {@link #lastError}. */
    private static final int MAX_ERROR_LEN = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GdprExportStatus status = GdprExportStatus.PENDING;

    /** The ZIP. Null until the handler builds it, and again once purged. */
    @ToString.Exclude
    @Column(name = "payload")
    private byte[] payload;

    /** Kept after the payload is purged, so the audit trail still shows what was served. */
    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public GdprExport(User user, Instant expiresAt) {
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public void markReady(byte[] archive) {
        this.payload = archive;
        this.byteSize = (long) archive.length;
        this.status = GdprExportStatus.READY;
        this.completedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(Throwable error) {
        this.status = GdprExportStatus.FAILED;
        String message = String.valueOf(error);
        this.lastError = message.length() <= MAX_ERROR_LEN ? message : message.substring(0, MAX_ERROR_LEN);
    }

    /** Drops the bytes but keeps the row as a record that the request was served. */
    public void purge() {
        this.payload = null;
        this.status = GdprExportStatus.EXPIRED;
    }

    public boolean isDownloadable(Instant now) {
        return status == GdprExportStatus.READY && payload != null && expiresAt.isAfter(now);
    }
}
