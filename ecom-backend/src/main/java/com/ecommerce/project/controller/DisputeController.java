package com.ecommerce.project.controller;

import com.ecommerce.project.payload.DisputeDTO;
import com.ecommerce.project.payload.DisputeEvidenceFileDTO;
import com.ecommerce.project.service.DisputeEvidenceService;
import com.ecommerce.project.service.DisputeService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin view of chargebacks and the evidence attached to them. Everything under
 * {@code /api/admin/**} is already {@code hasRole('ADMIN')} in the security
 * config. Disputes themselves are created by the Stripe webhook, never here.
 */
@Tag(name = "Dispute")
@RestController
@RequestMapping("/api/admin/disputes")
public class DisputeController extends BaseController {

    private final DisputeService disputeService;
    private final DisputeEvidenceService disputeEvidenceService;
    private final AuthUtil authUtil;

    public DisputeController(DisputeService disputeService, DisputeEvidenceService disputeEvidenceService,
                            AuthUtil authUtil) {
        this.disputeService = disputeService;
        this.disputeEvidenceService = disputeEvidenceService;
        this.authUtil = authUtil;
    }

    @GetMapping
    public ResponseEntity<List<DisputeDTO>> list() {
        return ok(disputeService.listAll());
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeDTO> get(@PathVariable Long disputeId) {
        return ok(disputeService.get(disputeId));
    }

    @PostMapping("/{disputeId}/evidence")
    public ResponseEntity<DisputeEvidenceFileDTO> uploadEvidence(@PathVariable Long disputeId,
                                                                @RequestParam("file") MultipartFile file) {
        return created(disputeEvidenceService.attach(disputeId, file, authUtil.loggedInEmail()));
    }

    @GetMapping("/{disputeId}/evidence/{fileId}")
    public ResponseEntity<Resource> downloadEvidence(@PathVariable Long disputeId, @PathVariable Long fileId) {
        DisputeEvidenceService.EvidenceDownload dl = disputeEvidenceService.download(disputeId, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .header("Content-Disposition",
                        ContentDisposition.attachment().filename(dl.filename()).build().toString())
                .body(new ByteArrayResource(dl.bytes()));
    }
}
