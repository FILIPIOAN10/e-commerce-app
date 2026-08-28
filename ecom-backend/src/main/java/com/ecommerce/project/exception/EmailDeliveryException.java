package com.ecommerce.project.exception;

/**
 * An email could not be handed to SMTP. Thrown (rather than swallowed) by the
 * order-email paths so the transactional outbox dispatcher sees the failure and
 * schedules a retry.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
