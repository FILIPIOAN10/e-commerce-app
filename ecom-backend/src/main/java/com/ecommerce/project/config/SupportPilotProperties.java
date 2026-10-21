package com.ecommerce.project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The AI support agent, bound from {@code app.support.*}.
 *
 * <p>SupportPilot is a separate Python service. The shop talks to it with two
 * keys it holds ({@link #gatewayApiKey} for customer traffic, {@link #adminApiKey}
 * for approvals), and SupportPilot talks back to the shop's
 * {@code /internal/support/**} API with a third ({@link #internalApiKey}). Three
 * keys, so a leak of one role's key does not hand over the others.
 *
 * <p>{@link #readTimeout} is long on purpose: one chat turn is a loop of model
 * calls plus a DocuAsk lookup, and is measured in tens of seconds, not millis.
 */
@Getter
@Setter
@ConfigurationProperties("app.support")
public class SupportPilotProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:8001";
    private String gatewayApiKey = "";
    private String adminApiKey = "";
    private String internalApiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(90);

    /** Days after delivery during which the agent may file a return (the policy in returns.md). */
    private int refundWindowDays = 30;
}
