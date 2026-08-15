package com.ecommerce.project.repository;

import com.ecommerce.project.model.PromoCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PromoCampaignRepository extends JpaRepository<PromoCampaign, Long> {
    List<PromoCampaign> findByActiveTrueAndStartTimeBeforeAndEndTimeAfter(LocalDateTime now, LocalDateTime now2);
}
