package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.IdempotencyConflictException;
import com.ecommerce.project.exception.IdempotencyKeyReusedException;
import com.ecommerce.project.model.IdempotencyKey;
import com.ecommerce.project.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Makes a state-changing request replay-safe. The caller passes an
 * {@code Idempotency-Key} (from the request header), a {@code scope} naming the
 * operation, the request payload, and a {@link Supplier} that performs the work.
 * <ul>
 *   <li>No key → the work runs unguarded (idempotency is opt-in).</li>
 *   <li>Key seen before, same request → the stored response is replayed; the
 *       work does not run again.</li>
 *   <li>Key seen before, different request → {@link IdempotencyKeyReusedException}
 *       (HTTP 422).</li>
 *   <li>Key currently in flight → {@link IdempotencyConflictException} (HTTP 409,
 *       retry).</li>
 * </ul>
 * Not {@code @Transactional}: each repository call is its own unit of work, so a
 * unique-constraint clash on the claim does not poison the caller's work, and
 * the {@code action} keeps whatever transaction it manages itself.
 */
@Service
public class IdempotencyService {

    /** An IN_PROGRESS claim older than this is assumed abandoned (caller crashed). */
    private static final Duration STALE_CLAIM = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository repository;

    /**
     * A private mapper, not the MVC one: this app exposes no {@code ObjectMapper}
     * bean, and the serialization here (request fingerprint, stored response) is
     * an internal detail that should not drift with MVC configuration.
     * {@code findAndAddModules} pulls in JSR-310 so {@code LocalDate} fields on
     * the response DTOs round-trip.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    public <T> ResponseEntity<T> runIdempotent(String idempotencyKey, String scope, Object request,
                                               Class<T> responseType, Supplier<ResponseEntity<T>> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String requestHash = sha256(scope + '\n' + serialize(request));

        Optional<IdempotencyKey> existing = repository.findByIdempotencyKeyAndScope(idempotencyKey, scope);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestHash, responseType);
        }

        IdempotencyKey claim = new IdempotencyKey(idempotencyKey, scope, requestHash);
        try {
            repository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException raced) {
            IdempotencyKey winner = repository.findByIdempotencyKeyAndScope(idempotencyKey, scope)
                    .orElseThrow(() -> new IdempotencyConflictException(
                            "Could not resolve this Idempotency-Key; please retry."));
            return replayOrReject(winner, requestHash, responseType);
        }

        ResponseEntity<T> response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            // The attempt failed; drop the claim so the same key can be retried.
            repository.delete(claim);
            throw e;
        }

        claim.complete(response.getStatusCode().value(), serialize(response.getBody()));
        repository.save(claim);
        return response;
    }

    private <T> ResponseEntity<T> replayOrReject(IdempotencyKey record, String requestHash, Class<T> responseType) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException(
                    "This Idempotency-Key was already used for a different request.");
        }

        if (record.getStatus() == IdempotencyKey.Status.IN_PROGRESS) {
            if (record.getCreatedAt().isBefore(Instant.now().minus(STALE_CLAIM))) {
                log.warn("Reclaiming stale in-progress idempotency key {} (scope {})",
                        record.getIdempotencyKey(), record.getScope());
                repository.delete(record);
                repository.flush();
                throw new IdempotencyConflictException(
                        "The previous attempt did not complete; please retry.");
            }
            throw new IdempotencyConflictException(
                    "A request with this Idempotency-Key is still being processed; please retry shortly.");
        }

        try {
            T body = objectMapper.readValue(record.getResponseBody(), responseType);
            return ResponseEntity.status(record.getResponseStatus()).body(body);
        } catch (JsonProcessingException e) {
            throw new IdempotencyConflictException(
                    "The stored response for this Idempotency-Key could not be read; please retry.");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new APIException("Could not serialize request or response: " + e.getMessage());
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
