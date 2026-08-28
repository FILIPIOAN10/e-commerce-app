package com.ecommerce.project.service.outbox;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.OutboxEvent;
import com.ecommerce.project.model.OutboxStatus;
import com.ecommerce.project.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F8: the transactional outbox delivers side effects durably. A handler failure
 * is retried with exponential backoff and dead-lettered after a cap — never
 * silently dropped. Driven through a controllable test handler so the state
 * machine is exercised without a real SMTP round-trip.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, OutboxProcessorTest.TestHandlerConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.outbox.max-attempts=3",
        "app.outbox.base-backoff-seconds=30"
})
class OutboxProcessorTest {

    static final String CONTROLLABLE = "TEST_CONTROLLABLE";
    static final String UNHANDLED = "TEST_NO_HANDLER";

    @TestConfiguration
    static class TestHandlerConfig {
        @Bean
        ControllableHandler controllableHandler() {
            return new ControllableHandler();
        }
    }

    static class ControllableHandler implements OutboxHandler {
        volatile boolean fail = false;
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String eventType() {
            return CONTROLLABLE;
        }

        @Override
        public void handle(String payload) {
            calls.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("handler asked to fail for " + payload);
            }
        }
    }

    @Autowired private OutboxEventPublisher publisher;
    @Autowired private OutboxProcessor processor;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private EntityManager entityManager;
    @Autowired private ControllableHandler handler;

    @BeforeEach
    void reset() {
        handler.fail = false;
        handler.calls.set(0);
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                entityManager.createNativeQuery("DELETE FROM outbox_event").executeUpdate());
    }

    private Long enqueue(String type) {
        return new TransactionTemplate(txManager).execute(status ->
                publisher.publish(type, Map.of("marker", type + "-" + System.nanoTime())).getId());
    }

    private void drainDueEvents() {
        while (processor.processBatch() > 0) {
            // until nothing is claimable
        }
    }

    private void forceDue(Long id) {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "UPDATE outbox_event SET next_attempt_at = now() - interval '1 hour' WHERE id = :id")
                        .setParameter("id", id)
                        .executeUpdate());
    }

    private OutboxEvent reload(Long id) {
        return new TransactionTemplate(txManager).execute(status ->
                outboxEventRepository.findById(id).orElseThrow());
    }

    @Test
    @DisplayName("a successful handler marks the event DONE exactly once")
    void successMarksDone() {
        Long id = enqueue(CONTROLLABLE);

        drainDueEvents();

        assertThat(reload(id).getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(handler.calls.get()).isEqualTo(1);

        // A second drain does not re-run a DONE event.
        drainDueEvents();
        assertThat(handler.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a failing handler backs off, retries, then dead-letters after max-attempts")
    void failureRetriesThenDeadLetters() {
        handler.fail = true;
        Long id = enqueue(CONTROLLABLE);

        drainDueEvents();
        OutboxEvent afterFirst = reload(id);
        assertThat(afterFirst.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(afterFirst.getLastError()).contains("handler asked to fail");

        for (int i = 0; i < 5 && reload(id).getStatus() == OutboxStatus.PENDING; i++) {
            forceDue(id);
            drainDueEvents();
        }

        OutboxEvent dead = reload(id);
        assertThat(dead.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(dead.getAttempts()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("an event with no registered handler is dead-lettered, not lost")
    void unknownTypeIsDeadLettered() {
        Long id = enqueue(UNHANDLED);

        for (int i = 0; i < 5 && reload(id).getStatus() == OutboxStatus.PENDING; i++) {
            forceDue(id);
            drainDueEvents();
        }

        assertThat(reload(id).getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(reload(id).getLastError()).contains("No OutboxHandler registered");
    }
}
