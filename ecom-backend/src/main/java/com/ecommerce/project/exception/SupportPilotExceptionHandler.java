package com.ecommerce.project.exception;

import com.ecommerce.project.controller.AdminSupportController;
import com.ecommerce.project.controller.SupportController;
import com.ecommerce.project.payload.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns {@link SupportPilotException} into its status. Ordered first because
 * {@link MyGlobalExceptionHandler} has a catch-all {@code Exception} handler,
 * and Spring picks the first advice that has <em>any</em> matching method — the
 * catch-all would win and answer 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {SupportController.class, AdminSupportController.class})
public class SupportPilotExceptionHandler {

    @ExceptionHandler(SupportPilotException.class)
    public ResponseEntity<ApiResponse> handle(SupportPilotException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.getStatus());
        if (e.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        }
        return response.body(new ApiResponse(e.getMessage(), false));
    }
}
