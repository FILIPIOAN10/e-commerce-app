package com.ecommerce.project.service.outbox;

import com.ecommerce.project.model.OutboxEvent;
import com.ecommerce.project.model.OutboxStatus;
import com.ecommerce.project.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Every transaction the outbox dispatcher opens, and nothing else.
 *
 * <p>The point of this class is the boundary it draws: each method here is one
 * short transaction that touches only the {@code outbox_event} table, and
 * {@link OutboxProcessor} — which does the slow work — has no transaction at all.
 * Before, {@code processBatch()} was itself {@code @Transactional}, so a whole
 * batch of handlers ran inside one transaction: the Stripe call in
 * {@code RefundHandler}, the SMTP round-trips in the email handlers, and the
 * account-wide archive build in {@code GdprExportHandler}. That transaction held
 * a HikariCP connection and the {@code FOR UPDATE SKIP LOCKED} row locks for the
 * whole time. A Stripe latency spike (its client defaults to an 80s read timeout)
 * against a batch of 20 could park one of 20 pooled connections for the better
 * part of half an hour while the storefront's own checkouts timed out on
 * {@code hikari.connection-timeout}. {@code StripeServiceImpl} documents exactly
 * this hazard for the request path; the outbox had reintroduced it on the
 * background path.
 *
 * <p>It lives as its own bean rather than as methods on the processor because
 * Spring's {@code @Transactional} is proxy-based: a self-invocation inside
 * {@code OutboxProcessor} would bypass the proxy and silently open no
 * transaction at all.
 */
@Slf4j
@Component
public class OutboxEventStore {

    private final OutboxEventRepository outboxEventRepository;
    private final Duration lease;

    public OutboxEventStore(OutboxEventRepository outboxEventRepository,
                            @Value("${app.outbox.lease-seconds:300}") long leaseSeconds) {
        this.outboxEventRepository = outboxEventRepository;
        this.lease = Duration.ofSeconds(leaseSeconds);
    }

    /**
     * Claims up to {@code batchSize} due events and returns them detached, for a
     * caller that will handle them with no transaction open.
     *
     * <p>The returned objects are read-only snapshots as far as the caller is
     * concerned: this transaction has committed by the time they are used, so
     * mutating them changes nothing. Outcomes go back through
     * {@link #recordSuccess(Long)} / {@link #recordFailure(Long, Throwable)},
     * which re-read the row.
     */
    @Transactional
    public List<OutboxEvent> claim(int batchSize) {
        List<Long> ids = outboxEventRepository.claimableIds(batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<OutboxEvent> claimed = outboxEventRepository.findAllById(ids);
        claimed.forEach(event -> event.markInProgress(lease));
        return claimed;
    }

    /** The handler returned normally. */
    @Transactional
    public void recordSuccess(Long eventId) {
        outboxEventRepository.findById(eventId).ifPresent(OutboxEvent::markDone);
    }

    /**
     * The handler threw. Backs the event off for another try, or dead-letters it
     * once the attempts budget claimed in {@link #claim(int)} is spent.
     *
     * <p>{@code REQUIRES_NEW} so that recording the failure survives even when
     * the caller is somehow inside a transaction that is going to roll back — the
     * whole point of the outbox is that a failed side effect stays visible.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long eventId, Throwable error, int maxAttempts, Duration baseBackoff) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.recordFailure(error, maxAttempts, baseBackoff);
            if (event.getStatus() == OutboxStatus.DEAD) {
                log.error("Outbox event {} ({}) dead-lettered after {} attempts: {}",
                        event.getId(), event.getEventType(), event.getAttempts(), error.toString());
            } else {
                log.warn("Outbox event {} ({}) failed attempt {}, retrying after backoff: {}",
                        event.getId(), event.getEventType(), event.getAttempts(), error.toString());
            }
        });
    }
}
