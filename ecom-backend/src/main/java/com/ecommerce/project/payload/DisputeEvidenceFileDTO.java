package com.ecommerce.project.payload;

import java.time.Instant;

public record DisputeEvidenceFileDTO(
        Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String uploadedBy,
        Instant uploadedAt) {
}
