package com.ecommerce.project.controller;

import com.ecommerce.project.payload.BundleDTO;
import com.ecommerce.project.service.BundleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BundleController {

    private final BundleService bundleService;

    @Tag(name = "Bundles")
    @GetMapping("/public/bundles")
    public ResponseEntity<List<BundleDTO>> getActiveBundles() {
        return ResponseEntity.ok(bundleService.getActiveBundles());
    }

    @Tag(name = "Bundles")
    @GetMapping("/public/bundles/{bundleId}")
    public ResponseEntity<BundleDTO> getBundleById(@PathVariable Long bundleId) {
        return ResponseEntity.ok(bundleService.getBundleById(bundleId));
    }

    @Tag(name = "Bundles")
    @GetMapping("/admin/bundles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BundleDTO>> getAllBundles() {
        return ResponseEntity.ok(bundleService.getAllBundles());
    }

    @Tag(name = "Bundles")
    @PostMapping("/admin/bundles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BundleDTO> createBundle(@Valid @RequestBody BundleDTO bundleDTO) {
        return new ResponseEntity<>(bundleService.createBundle(bundleDTO), HttpStatus.CREATED);
    }

    @Tag(name = "Bundles")
    @PutMapping("/admin/bundles/{bundleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BundleDTO> updateBundle(@PathVariable Long bundleId,
                                                  @Valid @RequestBody BundleDTO bundleDTO) {
        return ResponseEntity.ok(bundleService.updateBundle(bundleId, bundleDTO));
    }

    @Tag(name = "Bundles")
    @DeleteMapping("/admin/bundles/{bundleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BundleDTO> deleteBundle(@PathVariable Long bundleId) {
        return ResponseEntity.ok(bundleService.deleteBundle(bundleId));
    }
}
