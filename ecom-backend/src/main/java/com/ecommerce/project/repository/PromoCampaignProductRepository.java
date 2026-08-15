package com.ecommerce.project.repository;

import com.ecommerce.project.model.PromoCampaignProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromoCampaignProductRepository extends JpaRepository<PromoCampaignProduct, Long> {
    List<PromoCampaignProduct> findByPromoCampaignId(Long campaignId);
    List<PromoCampaignProduct> findByProductProductId(Long productId);
    void deleteByPromoCampaignId(Long campaignId);
}
