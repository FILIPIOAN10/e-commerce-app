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
     * Picks up to {@code limit} event ids that are due to run and locks their
     * rows. {@code FOR UPDATE SKIP LOCKED} lets several dispatcher instances (or
     * ticks) select from the table in parallel without ever handing the same row
     * to two of them.
     *
     * <p>The locks last only as long as the short claim transaction that calls
     * this — the handler runs afterwards, with nothing held. What keeps a second
     * dispatcher off the row for the duration of the handler is the lease written
     * by {@link com.ecommerce.project.model.OutboxEvent#markInProgress}, not this
     * lock.
     *
     * <p>{@code IN_PROGRESS} rows are claimable again once their lease has run
     * out: that is how an event whose dispatcher died mid-handler comes back,
     * without a separate reaper job.
     *
     * <p>Ids rather than entities, because the caller loads and mutates them in
     * the same transaction and a native {@code SELECT *} would hand back rows
     * Hibernate then has to reconcile with the persistence context.
     */
    @Query(value = """
            SELECT id FROM outbox_event
            WHERE status IN ('PENDING', 'IN_PROGRESS') AND next_attempt_at <= now()
            ORDER BY next_attempt_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> claimableIds(@Param("limit") int limit);

    long countByStatus(com.ecommerce.project.model.OutboxStatus status);
}
