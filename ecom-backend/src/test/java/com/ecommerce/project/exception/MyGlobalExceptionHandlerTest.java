package com.ecommerce.project.exception;

import com.ecommerce.project.payload.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MyGlobalExceptionHandler — conflict mappings")
class MyGlobalExceptionHandlerTest {

    private final MyGlobalExceptionHandler handler = new MyGlobalExceptionHandler();

    @Test
    @DisplayName("optimistic lock failure -> 409")
    void optimisticLockMapsToConflict() {
        ResponseEntity<ApiResponse> response =
                handler.handleOptimisticLock(new OptimisticLockingFailureException("row moved"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().isStatus()).isFalse();
        assertThat(response.getBody().getMessage()).contains("changed by someone else");
    }

    @Test
    @DisplayName("idempotency conflict -> 409")
    void idempotencyConflictMapsToConflict() {
        ResponseEntity<ApiResponse> response =
                handler.handleIdempotencyConflict(new IdempotencyConflictException("still processing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("still processing");
    }

    @Test
    @DisplayName("idempotency key reused -> 422")
    void idempotencyKeyReusedMapsToUnprocessable() {
        ResponseEntity<ApiResponse> response =
                handler.handleIdempotencyKeyReused(new IdempotencyKeyReusedException("different body"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getMessage()).isEqualTo("different body");
    }
}
