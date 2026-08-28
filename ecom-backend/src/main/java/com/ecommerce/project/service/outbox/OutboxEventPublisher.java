package com.ecommerce.project.service.outbox;

import com.ecommerce.project.model.OutboxEvent;
import com.ecommerce.project.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an outbox row for a side effect that is owed once the current
 * transaction commits.
 *
 * <p>{@link Propagation#MANDATORY}: this must run inside the business
 * transaction, so that the row and the business change are one atomic unit. A
 * caller with no active transaction is a bug and fails loudly here rather than
 * silently enqueuing work that may never have a matching commit.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPayloadCodec payloadCodec;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publish(String eventType, Object payload) {
        return outboxEventRepository.save(
                OutboxEvent.of(eventType, payloadCodec.serialize(payload)));
    }
}
