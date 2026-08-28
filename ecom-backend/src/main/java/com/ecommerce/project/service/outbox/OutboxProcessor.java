package com.ecommerce.project.service.outbox;

import com.ecommerce.project.model.OutboxEvent;
import com.ecommerce.project.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Drains one batch of due outbox events. Separated from {@link OutboxDispatcher}
 * so the batch runs in its own transaction (the dispatcher's {@code @Scheduled}
 * tick is not transactional) and so tests can drive it directly.
 */
@Slf4j
@Component
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxHandlerRegistry handlerRegistry;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration baseBackoff;

    public OutboxProcessor(OutboxEventRepository outboxEventRepository,
                           OutboxHandlerRegistry handlerRegistry,
                           @Value("${app.outbox.batch-size:20}") int batchSize,
                           @Value("${app.outbox.max-attempts:8}") int maxAttempts,
                           @Value("${app.outbox.base-backoff-seconds:30}") long baseBackoffSeconds) {
        this.outboxEventRepository = outboxEventRepository;
        this.handlerRegistry = handlerRegistry;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = Duration.ofSeconds(baseBackoffSeconds);
    }

    /**
     * Claims up to {@code batchSize} due events and dispatches each. A handler
     * failure is contained to its own row (attempt bumped, backed off, or
     * dead-lettered) and never rolls back the siblings in the batch.
     *
     * @return the number of events claimed this run (0 when the queue is drained)
     */
    @Transactional
    public int processBatch() {
        List<OutboxEvent> batch = outboxEventRepository.claimBatch(batchSize);
        for (OutboxEvent event : batch) {
            try {
                handlerRegistry.handlerFor(event.getEventType()).handle(event.getPayload());
                event.markDone();
            } catch (Exception ex) {
                event.recordFailure(ex, maxAttempts, baseBackoff);
                if (event.getStatus() == com.ecommerce.project.model.OutboxStatus.DEAD) {
                    log.error("Outbox event {} ({}) dead-lettered after {} attempts: {}",
                            event.getId(), event.getEventType(), event.getAttempts(), ex.toString());
                } else {
                    log.warn("Outbox event {} ({}) failed attempt {}, retrying after backoff: {}",
                            event.getId(), event.getEventType(), event.getAttempts(), ex.toString());
                }
            }
        }
        return batch.size();
    }
}
