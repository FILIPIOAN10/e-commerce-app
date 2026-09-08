package com.ecommerce.project.model;

/**
 * Lifecycle of an {@link OutboxEvent}.
 *
 * <ul>
 *   <li>{@code PENDING} — waiting to be dispatched (or waiting out a retry backoff).</li>
 *   <li>{@code IN_PROGRESS} — claimed by a dispatcher and being handled right now.
 *       The claim is a <em>lease</em>, not a lock: {@code next_attempt_at} holds the
 *       moment the lease runs out, after which any dispatcher may claim the row
 *       again. That is what makes a crash mid-handler recoverable without a
 *       separate reaper — see {@link OutboxEvent#markInProgress}.</li>
 *   <li>{@code DONE} — the side effect completed.</li>
 *   <li>{@code DEAD} — retries exhausted; needs a human.</li>
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    DEAD
}
