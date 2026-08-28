package com.ecommerce.project.exception;

/**
 * A request with the same {@code Idempotency-Key} is still being processed.
 * The client should retry after a short delay, when the original response will
 * be replayed. Mapped to HTTP 409.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
