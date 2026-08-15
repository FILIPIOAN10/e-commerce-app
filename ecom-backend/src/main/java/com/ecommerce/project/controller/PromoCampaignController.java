package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.PromoCampaignDTO;
import com.ecommerce.project.payload.PromoCampaignResponse;
import com.ecommerce.project.service.PromoCampaignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PromoCampaignController {

    private final PromoCampaignService promoCampaignService;

    @Tag(name = "Promo Campaigns")
    @GetMapping("/admin/promo-campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromoCampaignResponse> getCampaigns(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize) {
        return new ResponseEntity<>(promoCampaignService.getCampaigns(pageNumber, pageSize), HttpStatus.OK);
    }

    @Tag(name = "Promo Campaigns")
    @GetMapping("/admin/promo-campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromoCampaignDTO> getCampaign(@PathVariable Long id) {
        return new ResponseEntity<>(promoCampaignService.getCampaign(id), HttpStatus.OK);
    }

    @Tag(name = "Promo Campaigns")
    @PostMapping("/admin/promo-campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createCampaign(@RequestBody PromoCampaignDTO dto) {
        PromoCampaignDTO created = promoCampaignService.createCampaign(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @Tag(name = "Promo Campaigns")
    @PutMapping("/admin/promo-campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateCampaign(@PathVariable Long id, @RequestBody PromoCampaignDTO dto) {
        PromoCampaignDTO updated = promoCampaignService.updateCampaign(id, dto);
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    @Tag(name = "Promo Campaigns")
    @DeleteMapping("/admin/promo-campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteCampaign(@PathVariable Long id) {
        promoCampaignService.deleteCampaign(id);
        return new ResponseEntity<>(Map.of("message", "Campaign deleted"), HttpStatus.OK);
    }
}
