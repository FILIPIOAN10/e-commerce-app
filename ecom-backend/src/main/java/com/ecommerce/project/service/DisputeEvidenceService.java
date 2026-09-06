package com.ecommerce.project.service;

import com.ecommerce.project.payload.DisputeEvidenceFileDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin-uploaded evidence for a dispute. The bytes go through {@code FileService}
 * (a non-web-served directory by default, S3 when configured) — only this service
 * and the admin download endpoint can read them back.
 */
public interface DisputeEvidenceService {

    DisputeEvidenceFileDTO attach(Long disputeId, MultipartFile file, String adminEmail);

    /** The file's content, for the admin download endpoint. */
    EvidenceDownload download(Long disputeId, Long fileId);

    record EvidenceDownload(String filename, String contentType, byte[] bytes) {
    }
}
