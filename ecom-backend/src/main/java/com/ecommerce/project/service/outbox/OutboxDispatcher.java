package com.ecommerce.project.service.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox on a fixed delay and hands each due batch to
 * {@link OutboxProcessor}. Keeps draining while batches come back full, so a
 * backlog clears without waiting a whole poll interval per batch.
 *
 * <p>Multiple instances are safe: the claim query uses {@code FOR UPDATE SKIP
 * LOCKED}, so two pollers never take the same row. Set {@code app.outbox.enabled=false}
 * to turn the poller off (tests drive {@link OutboxProcessor} directly).
 */
@Slf4j
@Component
public class OutboxDispatcher {

    private final OutboxProcessor processor;
    private final boolean enabled;
    private final int maxBatchesPerTick;

    public OutboxDispatcher(OutboxProcessor processor,
                            @Value("${app.outbox.enabled:true}") boolean enabled,
                            @Value("${app.outbox.max-batches-per-tick:10}") int maxBatchesPerTick) {
        this.processor = processor;
        this.enabled = enabled;
        this.maxBatchesPerTick = maxBatchesPerTick;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.poll-interval-ms:5000}",
            initialDelayString = "${app.outbox.initial-delay-ms:15000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        try {
            for (int i = 0; i < maxBatchesPerTick; i++) {
                if (processor.processBatch() == 0) {
                    break;
                }
            }
        } catch (Exception e) {
            // Never let a bad tick kill the scheduler thread.
            log.error("Outbox dispatch tick failed", e);
        }
    }
}
