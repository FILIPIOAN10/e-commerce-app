package com.ecommerce.project.service.outbox;

/**
 * Performs one type of outbox side effect.
 *
 * <p>Implementations must be <strong>idempotent</strong>: the dispatcher
 * guarantees at-least-once delivery, so the same payload can arrive more than
 * once (a retry after the effect succeeded but the status write did not, a
 * dispatcher crash mid-batch, ...). Throw to signal failure — the dispatcher
 * will back off and retry, then dead-letter.
 */
public interface OutboxHandler {

    /** The {@link OutboxEventTypes} value this handler consumes. */
    String eventType();

    /**
     * Performs the side effect. Throw (any unchecked exception) to signal failure
     * — the dispatcher backs off and retries, then dead-letters.
     *
     * @param payload the JSON string stored on the outbox row
     */
    void handle(String payload);
}
