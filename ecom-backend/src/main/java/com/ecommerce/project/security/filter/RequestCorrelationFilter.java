package com.ecommerce.project.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a correlation id on every request: taken from the inbound
 * {@code X-Request-Id} header when a caller (or an upstream proxy) supplies one,
 * generated otherwise. It is exposed to logs through the MDC key
 * {@code requestId} (see {@code logback-spring.xml}), echoed back on the response
 * header, and attached to error bodies by the global exception handler — so a
 * single line a customer reports can be traced across the request's log entries.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** A generous cap so a hostile header cannot bloat every log line. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        // Keep it to characters that are safe in a log line and a header value.
        String cleaned = trimmed.replaceAll("[^A-Za-z0-9._-]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static String currentRequestId() {
        return MDC.get(MDC_KEY);
    }
}
