package com.ecommerce.project.service.support;

import com.ecommerce.project.config.SupportPilotProperties;
import com.ecommerce.project.exception.SupportPilotException;
import com.ecommerce.project.security.filter.RequestCorrelationFilter;
import com.ecommerce.project.service.support.SupportPilotApi.ApprovalOut;
import com.ecommerce.project.service.support.SupportPilotApi.ChatIn;
import com.ecommerce.project.service.support.SupportPilotApi.ChatOut;
import com.ecommerce.project.service.support.SupportPilotApi.ConversationOut;
import com.ecommerce.project.service.support.SupportPilotApi.DecisionIn;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * HTTP client for SupportPilot (the Python agent service). The only class that
 * knows its URLs, headers and keys.
 *
 * <p>The shop is SupportPilot's trusted gateway: it authenticates the customer
 * from the JWT cookie, then speaks for them with {@code X-Customer-Id}. That
 * header is believed only because the gateway key comes with it, so the gateway
 * key must never reach a browser.
 *
 * <p>Every failure comes out as a {@link SupportPilotException} carrying the
 * status and the sentence the caller should see.
 */
@Slf4j
@Component
public class SupportPilotClient {

    private static final String UNAVAILABLE = "The support assistant is unavailable right now. Please try again in a few minutes.";

    private final SupportPilotProperties properties;
    private final RestClient restClient;

    public SupportPilotClient(SupportPilotProperties properties) {
        this.properties = properties;
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.getConnectTimeout(), properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                // One request id across both services' logs.
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get(RequestCorrelationFilter.MDC_KEY);
                    if (requestId != null) {
                        request.getHeaders().set("X-Request-ID", requestId);
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    // --- customer side (gateway key + X-Customer-Id) -------------------------------

    public ChatOut chat(String customerId, String message, String conversationId) {
        return call(() -> restClient.post()
                .uri("/v1/chat")
                .headers(h -> customerHeaders(h, customerId))
                .body(new ChatIn(message, conversationId))
                .retrieve()
                .body(ChatOut.class));
    }

    public ConversationOut conversation(String customerId, String conversationId) {
        return call(() -> restClient.get()
                .uri("/v1/conversations/{id}", conversationId)
                .headers(h -> customerHeaders(h, customerId))
                .retrieve()
                .body(ConversationOut.class));
    }

    // --- admin side (admin key + X-Admin-User) --------------------------------------

    public List<ApprovalOut> pendingApprovals() {
        return call(() -> restClient.get()
                .uri("/v1/admin/approvals")
                .headers(h -> h.set("X-Admin-Key", properties.getAdminApiKey()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ApprovalOut>>() { }));
    }

    public ChatOut decide(String conversationId, String decision, String note, String adminUser) {
        return call(() -> restClient.post()
                .uri("/v1/admin/approvals/{id}", conversationId)
                .headers(h -> {
                    h.set("X-Admin-Key", properties.getAdminApiKey());
                    h.set("X-Admin-User", adminUser);
                })
                .body(new DecisionIn(decision, note))
                .retrieve()
                .body(ChatOut.class));
    }

    /** GDPR Art. 17: every conversation, approval and checkpoint of this customer. Idempotent. */
    public void eraseCustomer(String customerId) {
        call(() -> restClient.delete()
                .uri("/v1/admin/customers/{id}", customerId)
                .headers(h -> h.set("X-Admin-Key", properties.getAdminApiKey()))
                .retrieve()
                .toBodilessEntity());
    }

    // --- plumbing -------------------------------------------------------------------

    private void customerHeaders(HttpHeaders headers, String customerId) {
        headers.set("X-Api-Key", properties.getGatewayApiKey());
        headers.set("X-Customer-Id", customerId);
    }

    private <T> T call(Supplier<T> request) {
        if (!properties.isEnabled()) {
            throw new SupportPilotException(HttpStatus.SERVICE_UNAVAILABLE, "The support assistant is not enabled.");
        }
        try {
            return request.get();
        } catch (ResourceAccessException e) { // connect refused, read timeout
            log.warn("SupportPilot unreachable: {}", e.getMessage());
            throw new SupportPilotException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }

    private SupportPilotException translate(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        return switch (status) {
            case 404 -> new SupportPilotException(HttpStatus.NOT_FOUND, "No such conversation, or nothing is waiting for a decision there.");
            case 409 -> new SupportPilotException(HttpStatus.CONFLICT,
                    "A colleague is reviewing your refund request. The answer will appear in this conversation.");
            case 422 -> new SupportPilotException(HttpStatus.BAD_REQUEST, "The message could not be accepted.");
            case 429 -> new SupportPilotException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many messages, please slow down.", retryAfter(response));
            default -> {
                // 401 here means OUR key is wrong: a deployment problem, not the user's.
                log.error("SupportPilot answered {}", status);
                yield new SupportPilotException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
            }
        };
    }

    private Long retryAfter(ClientHttpResponse response) {
        String value = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
