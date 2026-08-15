package com.ecommerce.project.service;

import com.ecommerce.project.payload.PromoCampaignDTO;
import com.ecommerce.project.payload.PromoCampaignResponse;

public interface PromoCampaignService {
    PromoCampaignDTO createCampaign(PromoCampaignDTO dto);
    PromoCampaignDTO updateCampaign(Long id, PromoCampaignDTO dto);
    void deleteCampaign(Long id);
    PromoCampaignResponse getCampaigns(Integer pageNumber, Integer pageSize);
    void applyActiveCampaigns();
    PromoCampaignDTO getCampaign(Long id);
}
