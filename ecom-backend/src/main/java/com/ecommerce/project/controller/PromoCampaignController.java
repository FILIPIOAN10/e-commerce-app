package com.ecommerce.project.controller;

import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.payload.PromoCampaignDTO;
import com.ecommerce.project.payload.PromoCampaignResponse;
import com.ecommerce.project.service.PromoCampaignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PromoCampaignController extends BaseController {

    private final PromoCampaignService promoCampaignService;

    @Tag(name = "Promo Campaigns")
    @GetMapping("/admin/promo-campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromoCampaignResponse> getCampaigns(@ModelAttribute PaginationParams params) {
        return ok(promoCampaignService.getCampaigns(params.getPageNumber(), params.getPageSize()));
    }

    @Tag(name = "Promo Campaigns")
    @GetMapping("/admin/promo-campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromoCampaignDTO> getCampaign(@PathVariable Long id) {
        return ok(promoCampaignService.getCampaign(id));
    }

    @Tag(name = "Promo Campaigns")
    @PostMapping("/admin/promo-campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createCampaign(@Valid @RequestBody PromoCampaignDTO dto) {
        PromoCampaignDTO created = promoCampaignService.createCampaign(dto);
        return created(created);
    }

    @Tag(name = "Promo Campaigns")
    @PutMapping("/admin/promo-campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateCampaign(@PathVariable Long id, @Valid @RequestBody PromoCampaignDTO dto) {
        PromoCampaignDTO updated = promoCampaignService.updateCampaign(id, dto);
        return ok(updated);
    }

    @Tag(name = "Promo Campaigns")
    @DeleteMapping("/admin/promo-campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteCampaign(@PathVariable Long id) {
        promoCampaignService.deleteCampaign(id);
        return ok(Map.of("message", "Campaign deleted"));
    }
}
