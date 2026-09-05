package com.ecommerce.project.repository;

import com.ecommerce.project.model.PromoCampaignProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromoCampaignProductRepository extends JpaRepository<PromoCampaignProduct, Long> {
    List<PromoCampaignProduct> findByPromoCampaignId(Long campaignId);
    List<PromoCampaignProduct> findByProductProductId(Long productId);
    void deleteByPromoCampaignId(Long campaignId);

    /**
     * Links with their products already loaded, for the sweep.
     *
     * <p>{@code findByPromoCampaignId} leaves {@code product} as a lazy proxy,
     * so touching it in the apply/revert loop cost one SELECT per link. The
     * fetch join makes a campaign of any size two queries instead of 1 + N.
     */
    @Query("SELECT l FROM PromoCampaignProduct l JOIN FETCH l.product "
         + "WHERE l.promoCampaign.id = :campaignId")
    List<PromoCampaignProduct> findByCampaignIdWithProduct(@Param("campaignId") Long campaignId);
}
