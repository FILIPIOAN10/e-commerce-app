package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Dispute;
import com.ecommerce.project.model.DisputeEvidenceFile;
import com.ecommerce.project.payload.DisputeEvidenceFileDTO;
import com.ecommerce.project.repository.DisputeEvidenceFileRepository;
import com.ecommerce.project.repository.DisputeRepository;
import com.ecommerce.project.service.DisputeEvidenceService;
import com.ecommerce.project.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Stores and retrieves dispute evidence files. Upload validation lives here (type
 * + size) rather than only at the controller, so any caller is held to it; where
 * the bytes actually go is {@code FileService}'s business.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeEvidenceServiceImpl implements DisputeEvidenceService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg");

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceFileRepository evidenceFileRepository;
    private final FileService fileService;

    @Value("${app.disputes.evidence-dir:./dispute-evidence}")
    private String evidenceDir;

    @Value("${app.disputes.max-evidence-bytes:10485760}") // 10 MiB
    private long maxBytes;

    @Override
    @Transactional
    public DisputeEvidenceFileDTO attach(Long disputeId, MultipartFile file, String adminEmail) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", "id", disputeId));
        if (dispute.isTerminal()) {
            throw new APIException("Dispute " + disputeId + " is already " + dispute.getStatus()
                    + "; evidence can no longer be attached");
        }
        validate(file);

        String storedName;
        try {
            storedName = fileService.uploadImage(evidenceDir, file);
        } catch (IOException e) {
            throw new APIException("Could not store evidence file: " + e.getMessage());
        }

        DisputeEvidenceFile saved = evidenceFileRepository.save(DisputeEvidenceFile.of(
                disputeId, storedName, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), adminEmail));
        log.info("Evidence '{}' ({} bytes) attached to dispute {} by {}",
                file.getOriginalFilename(), file.getSize(), disputeId, adminEmail);

        return new DisputeEvidenceFileDTO(saved.getId(), saved.getOriginalName(), saved.getContentType(),
                saved.getSizeBytes(), saved.getUploadedBy(), saved.getUploadedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceDownload download(Long disputeId, Long fileId) {
        DisputeEvidenceFile meta = evidenceFileRepository.findById(fileId)
                .filter(f -> f.getDisputeId().equals(disputeId))
                .orElseThrow(() -> new ResourceNotFoundException("Evidence file", "id", fileId));
        try {
            byte[] bytes = fileService.read(evidenceDir, meta.getStoredName());
            return new EvidenceDownload(meta.getOriginalName(),
                    meta.getContentType() != null ? meta.getContentType() : "application/octet-stream", bytes);
        } catch (IOException e) {
            throw new APIException("Could not read evidence file " + fileId + ": " + e.getMessage());
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new APIException("No file provided");
        }
        if (file.getSize() > maxBytes) {
            throw new APIException("Evidence file is larger than the " + (maxBytes / (1024 * 1024)) + " MiB limit");
        }
        String type = file.getContentType();
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new APIException("Unsupported evidence type: " + type + ". Allowed: PDF, PNG, JPEG");
        }
    }
}
