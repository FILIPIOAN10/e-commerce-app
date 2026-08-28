package com.ecommerce.project.service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

/**
 * JSON (de)serialisation for outbox payloads.
 *
 * <p>A private mapper, not an MVC one — this app exposes no {@code ObjectMapper}
 * bean, and the on-disk payload format is an internal detail that must not drift
 * with web configuration. {@code findAndAddModules} pulls in JSR-310 so
 * {@code LocalDate} fields on the DTOs round-trip.
 */
@Component
public class OutboxPayloadCodec {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Outbox payload is not serialisable: " + payload.getClass().getName(), e);
        }
    }

    public <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Outbox payload cannot be read as " + type.getName(), e);
        }
    }
}
