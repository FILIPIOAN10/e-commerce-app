package com.ecommerce.project.service.outbox;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps an outbox event type to the {@link OutboxHandler} that consumes it.
 * Populated from every {@code OutboxHandler} bean at startup.
 */
@Component
public class OutboxHandlerRegistry {

    private final Map<String, OutboxHandler> byType;

    public OutboxHandlerRegistry(List<OutboxHandler> handlers) {
        this.byType = handlers.stream().collect(Collectors.toMap(
                OutboxHandler::eventType,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException(
                            "Two OutboxHandler beans claim event type " + a.eventType());
                }));
    }

    public OutboxHandler handlerFor(String eventType) {
        OutboxHandler handler = byType.get(eventType);
        if (handler == null) {
            throw new IllegalStateException("No OutboxHandler registered for event type " + eventType);
        }
        return handler;
    }
}
