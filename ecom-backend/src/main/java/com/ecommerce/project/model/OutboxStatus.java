package com.ecommerce.project.model;

/**
 * Lifecycle of an {@link OutboxEvent}.
 *
 * <ul>
 *   <li>{@code PENDING} — waiting to be dispatched (or waiting out a retry backoff).</li>
 *   <li>{@code DONE} — the side effect completed.</li>
 *   <li>{@code DEAD} — retries exhausted; needs a human.</li>
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    DONE,
    DEAD
}
