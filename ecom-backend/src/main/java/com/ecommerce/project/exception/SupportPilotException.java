package com.ecommerce.project.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A SupportPilot call that did not produce an answer, already translated into
 * what the shop's caller should see. The message is written for the customer or
 * admin; SupportPilot's own error text is logged, never passed through.
 */
@Getter
public class SupportPilotException extends RuntimeException {

    private final HttpStatus status;
    /** Seconds until a rate-limited customer may write again; null otherwise. */
    private final Long retryAfterSeconds;

    public SupportPilotException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public SupportPilotException(HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
