package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Metadata for one evidence file an admin uploaded for a {@link Dispute}. The
 * bytes themselves live wherever {@code FileService} put them (a local directory
 * by default, S3 when configured); {@code storedName} is the handle to fetch
 * them back by.
 */
@Entity
@Table(name = "dispute_evidence_files")
@Getter
@Setter
@NoArgsConstructor
public class DisputeEvidenceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispute_id", nullable = false)
    private Long disputeId;

    @Column(name = "stored_name", nullable = false)
    private String storedName;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    public static DisputeEvidenceFile of(Long disputeId, String storedName, String originalName,
                                         String contentType, long sizeBytes, String uploadedBy) {
        DisputeEvidenceFile f = new DisputeEvidenceFile();
        f.disputeId = disputeId;
        f.storedName = storedName;
        f.originalName = originalName;
        f.contentType = contentType;
        f.sizeBytes = sizeBytes;
        f.uploadedBy = uploadedBy;
        return f;
    }
}
