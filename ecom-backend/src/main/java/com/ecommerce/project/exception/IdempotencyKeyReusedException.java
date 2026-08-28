package com.ecommerce.project.exception;

/**
 * The same {@code Idempotency-Key} was sent with a different request body, so it
 * cannot safely be treated as a retry. Mapped to HTTP 422.
 */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String message) {
        super(message);
    }
}
