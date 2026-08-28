package com.ecommerce.project.repository;

import com.ecommerce.project.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims up to {@code limit} events that are due to run. {@code FOR UPDATE
     * SKIP LOCKED} lets several dispatcher instances (or ticks) drain the table
     * in parallel without ever handing the same row to two of them; the locks are
     * held until the calling transaction ends, by which point each claimed row
     * has been marked DONE or rescheduled.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING' AND next_attempt_at <= now()
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("limit") int limit);

    long countByStatus(com.ecommerce.project.model.OutboxStatus status);
}
