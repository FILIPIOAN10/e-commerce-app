package com.ecommerce.project.repository;

import com.ecommerce.project.model.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, Long> {

    boolean existsByEventId(String eventId);

    Optional<ProcessedWebhookEvent> findByEventId(String eventId);
}
