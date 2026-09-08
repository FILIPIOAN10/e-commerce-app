package com.ecommerce.project.service.outbox;

import com.ecommerce.project.model.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Drains one batch of due outbox events.
 *
 * <p><strong>Deliberately not {@code @Transactional}.</strong> Handlers do slow,
 * external work — Stripe refunds, SMTP sends, building and writing a GDPR
 * archive — and none of it may happen with a database transaction open. Every
 * transaction this needs is a short one inside {@link OutboxEventStore}: one to
 * claim the batch, then one per event to record its outcome. Between them the
 * dispatcher holds no connection and no row lock, so a slow third party can no
 * longer starve the connection pool that the storefront is also drawing from.
 *
 * <p>The claim is a lease rather than a lock (see
 * {@link com.ecommerce.project.model.OutboxEvent#markInProgress}), which is what
 * lets the row survive this process dying mid-handler.
 */
@Slf4j
@Component
public class OutboxProcessor {

    private final OutboxEventStore store;
    private final OutboxHandlerRegistry handlerRegistry;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration baseBackoff;

    public OutboxProcessor(OutboxEventStore store,
                           OutboxHandlerRegistry handlerRegistry,
                           @Value("${app.outbox.batch-size:20}") int batchSize,
                           @Value("${app.outbox.max-attempts:8}") int maxAttempts,
                           @Value("${app.outbox.base-backoff-seconds:30}") long baseBackoffSeconds) {
        this.store = store;
        this.handlerRegistry = handlerRegistry;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = Duration.ofSeconds(baseBackoffSeconds);
    }

    /**
     * Claims up to {@code batchSize} due events and dispatches each. A handler
     * failure is contained to its own row (backed off or dead-lettered) and never
     * affects the siblings in the batch — they are separate transactions now, so
     * that containment no longer depends on catching the exception in time.
     *
     * @return the number of events claimed this run (0 when the queue is drained)
     */
    public int processBatch() {
        List<OutboxEvent> batch = store.claim(batchSize);

        for (OutboxEvent event : batch) {
            try {
                handlerRegistry.handlerFor(event.getEventType()).handle(event.getPayload());
                store.recordSuccess(event.getId());
            } catch (Exception ex) {
                store.recordFailure(event.getId(), ex, maxAttempts, baseBackoff);
            }
        }
        return batch.size();
    }
}
