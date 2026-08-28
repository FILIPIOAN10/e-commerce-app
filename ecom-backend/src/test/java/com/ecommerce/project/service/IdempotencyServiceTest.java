package com.ecommerce.project.service;

import com.ecommerce.project.exception.IdempotencyConflictException;
import com.ecommerce.project.exception.IdempotencyKeyReusedException;
import com.ecommerce.project.model.IdempotencyKey;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderRequestDTO;
import com.ecommerce.project.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IdempotencyService")
class IdempotencyServiceTest {

    private static final String KEY = "abc-123";
    private static final String SCOPE = "place-order:buyer@example.com:STRIPE";

    @Mock private IdempotencyKeyRepository repository;

    private IdempotencyService service;
    private OrderRequestDTO request;
    private AtomicInteger actionRuns;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository);
        request = new OrderRequestDTO(7L, "STRIPE", "Stripe", "pi_1", "succeeded", "OK", List.of(), null);
        actionRuns = new AtomicInteger();
    }

    private ResponseEntity<OrderDTO> action() {
        actionRuns.incrementAndGet();
        OrderDTO dto = new OrderDTO();
        dto.setOrderId(99L);
        dto.setTotalAmount(new BigDecimal("42.00"));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    private ResponseEntity<OrderDTO> run(String key, Object req) {
        return service.runIdempotent(key, SCOPE, req, OrderDTO.class, this::action);
    }

    @Test
    @DisplayName("no key: runs the action once and never touches the store")
    void noKeyPassesThrough() {
        ResponseEntity<OrderDTO> response = run(null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(actionRuns).hasValue(1);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("first call: claims the key, runs the action, stores the response as COMPLETED")
    void firstCallStoresResponse() {
        when(repository.findByIdempotencyKeyAndScope(KEY, SCOPE)).thenReturn(Optional.empty());

        ResponseEntity<OrderDTO> response = run(KEY, request);

        assertThat(actionRuns).hasValue(1);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<IdempotencyKey> saved = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository).save(saved.capture());
        IdempotencyKey record = saved.getValue();
        assertThat(record.getStatus()).isEqualTo(IdempotencyKey.Status.COMPLETED);
        assertThat(record.getResponseStatus()).isEqualTo(201);
        assertThat(record.getResponseBody()).contains("\"orderId\":99");
    }

    @Test
    @DisplayName("repeat with the same request: replays the stored response, action does not run")
    void repeatReplays() {
        IdempotencyKey completed = completedRecord(request);
        when(repository.findByIdempotencyKeyAndScope(KEY, SCOPE)).thenReturn(Optional.of(completed));

        ResponseEntity<OrderDTO> response = run(KEY, request);

        assertThat(actionRuns).hasValue(0);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getOrderId()).isEqualTo(99L);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("same key, different request body: 422, action does not run")
    void differentRequestRejected() {
        IdempotencyKey completed = completedRecord(request);
        when(repository.findByIdempotencyKeyAndScope(KEY, SCOPE)).thenReturn(Optional.of(completed));

        OrderRequestDTO other = new OrderRequestDTO(8L, "STRIPE", "Stripe", "pi_2", "succeeded", "OK", List.of(), null);

        assertThatThrownBy(() -> run(KEY, other)).isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(actionRuns).hasValue(0);
    }

    @Test
    @DisplayName("key still in flight: 409, action does not run")
    void inProgressConflicts() {
        IdempotencyKey inProgress = new IdempotencyKey(KEY, SCOPE, hashOf(request));
        inProgress.setCreatedAt(Instant.now());
        when(repository.findByIdempotencyKeyAndScope(KEY, SCOPE)).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> run(KEY, request)).isInstanceOf(IdempotencyConflictException.class);
        assertThat(actionRuns).hasValue(0);
    }

    @Test
    @DisplayName("stale in-flight claim is dropped so the request can be retried")
    void staleInProgressReclaimed() {
        IdempotencyKey stale = new IdempotencyKey(KEY, SCOPE, hashOf(request));
        stale.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(repository.findByIdempotencyKeyAndScope(KEY, SCOPE)).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> run(KEY, request)).isInstanceOf(IdempotencyConflictException.class);
        verify(repository).delete(stale);
    }

    @Test
    @DisplayName("lost the claim race: re-reads the winner and replays it")
    void lostRaceReplaysWinner() {
        when(repository.findByIdempotencyKeyAndScope(KEY, SCOPE))
                .thenReturn(Optional.empty())                       // pre-check
                .thenReturn(Optional.of(completedRecord(request))); // after the clash
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup key"));

        ResponseEntity<OrderDTO> response = run(KEY, request);

        assertThat(actionRuns).hasValue(0);
        assertThat(response.getBody().getOrderId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("action failure drops the claim so the key can be retried")
    void actionFailureReleasesKey() {
        when(repository.findByIdempotencyKeyAndScope(KEY, SCOPE)).thenReturn(Optional.empty());

        ArgumentCaptor<IdempotencyKey> claim = ArgumentCaptor.forClass(IdempotencyKey.class);

        assertThatThrownBy(() -> service.runIdempotent(KEY, SCOPE, request, OrderDTO.class, () -> {
            throw new IllegalStateException("payment declined");
        })).isInstanceOf(IllegalStateException.class);

        verify(repository).saveAndFlush(claim.capture());
        verify(repository).delete(claim.getValue());
        verify(repository, never()).save(any());
    }

    private String hashOf(Object req) {
        // Mirrors IdempotencyService: SHA-256 of "scope\n" + json(req).
        try {
            ObjectMapper om = new ObjectMapper();
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((SCOPE + '\n' + om.writeValueAsString(req)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private IdempotencyKey completedRecord(Object req) {
        IdempotencyKey record = new IdempotencyKey(KEY, SCOPE, hashOf(req));
        record.setCreatedAt(Instant.now());
        record.complete(201, "{\"orderId\":99,\"totalAmount\":42.0}");
        return record;
    }
}
