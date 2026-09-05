package com.ecommerce.project.repository;

import com.ecommerce.project.model.PromoCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PromoCampaignRepository extends JpaRepository<PromoCampaign, Long> {
    List<PromoCampaign> findByActiveTrueAndStartTimeBeforeAndEndTimeAfter(LocalDateTime now, LocalDateTime now2);

    /**
     * Campaigns whose prices should be on their products but are not yet:
     * running now, and not already applied by an earlier pass.
     */
    List<PromoCampaign> findByActiveTrueAndAppliedFalseAndStartTimeBeforeAndEndTimeAfter(
            LocalDateTime startBefore, LocalDateTime endAfter);

    /**
     * Campaigns whose prices are on their products but should not be — ended,
     * switched off, or rescheduled to start later. These are the ones whose
     * discounts used to be left on the products permanently.
     */
    @Query("SELECT c FROM PromoCampaign c WHERE c.applied = true "
         + "AND (c.active = false OR c.endTime <= :now OR c.startTime > :now)")
    List<PromoCampaign> findAppliedButNotRunning(@Param("now") LocalDateTime now);
}
