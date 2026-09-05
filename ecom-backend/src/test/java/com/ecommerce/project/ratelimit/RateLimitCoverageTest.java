package com.ecommerce.project.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule table covers every endpoint that is cheap to call and expensive to
 * serve.
 *
 * <p>Ten rules protected sign-in, sign-up, search and the authenticated cart and
 * checkout, and stopped there. The gaps were the endpoints that need no account
 * at all: three that send mail to an address the caller names — an email cannon
 * pointed at any inbox, and a fast way to burn the SMTP quota and the sending
 * domain's reputation — and guest checkout, which is {@code permitAll} and
 * writes an address row, an order, consumed stock and redeemed coupons on every
 * call. {@code order-payment} only ever covered the authenticated path.
 *
 * <p>This asserts the table, not the filter: {@code RedisRateLimitServiceTest}
 * covers the counting. What is worth pinning here is that a rule for these
 * routes exists at all, since the failure mode is a silent absence.
 */
@DisplayName("Rate limit rule coverage")
class RateLimitCoverageTest {

    private List<RateLimitRule> rules;

    @BeforeEach
    void buildRules() {
        RateLimitConfig config = new RateLimitConfig();
        ReflectionTestUtils.setField(config, "rateLimitEnabled", true);
        rules = config.rateLimitRules();
    }

    @ParameterizedTest(name = "{0} {1} is rate limited")
    @CsvSource({
            // Unauthenticated and sends mail.
            "POST, /api/auth/forgot-password",
            "POST, /api/auth/resend-verification",
            "POST, /api/public/contact",
            // Unauthenticated and writes orders, stock and coupon redemptions.
            "POST, /api/public/orders/guest",
            // Authenticated, but answers whether a short guessable code exists.
            "POST, /api/coupons/validate",
            // The pre-existing rules, so removing one is also a failure.
            "POST, /api/auth/signin",
            "POST, /api/auth/signup",
            "POST, /api/order/users/payments/*",
    })
    void sensitiveEndpointsHaveARule(String method, String path) {
        assertThat(ruleFor(method, path))
                .as("no rate limit rule for %s %s", method, path)
                .isPresent();
    }

    @ParameterizedTest(name = "{0} allows at most {1} per minute")
    @CsvSource({
            "/api/auth/forgot-password, 3",
            "/api/auth/resend-verification, 3",
            "/api/public/contact, 3",
            "/api/public/orders/guest, 5",
    })
    void mailAndGuestOrderCapsStayLow(String path, long expectedLimit) {
        RateLimitRule rule = ruleFor("POST", path).orElseThrow();
        assertThat(rule.getLimit()).isEqualTo(expectedLimit);
        assertThat(rule.getWindow().toMinutes()).isEqualTo(1);
    }

    @Test
    @DisplayName("unauthenticated rules key on IP, since there is no user to key on")
    void unauthenticatedRulesKeyOnIp() {
        List<String> unauthenticated = List.of(
                "/api/auth/forgot-password", "/api/auth/resend-verification",
                "/api/public/contact", "/api/public/orders/guest");

        for (String path : unauthenticated) {
            RateLimitRule rule = ruleFor("POST", path).orElseThrow();
            assertThat(rule.getKeyType())
                    .as("%s has no authenticated user to key on", path)
                    .isEqualTo(RateLimitKeyType.IP);
        }
    }

    @Test
    @DisplayName("the whole table switches off with rate.limit.enabled")
    void disabledConfigYieldsNoRules() {
        RateLimitConfig config = new RateLimitConfig();
        ReflectionTestUtils.setField(config, "rateLimitEnabled", false);
        assertThat(config.rateLimitRules()).isEmpty();
    }

    @Test
    @DisplayName("rule names are unique, so one cannot silently shadow another")
    void ruleNamesAreUnique() {
        assertThat(rules.stream().map(RateLimitRule::getName).distinct().count())
                .isEqualTo(rules.size());
    }

    private Optional<RateLimitRule> ruleFor(String method, String path) {
        return rules.stream()
                .filter(r -> r.getMethod().equalsIgnoreCase(method))
                .filter(r -> r.getPathPattern().equals(path))
                .findFirst();
    }
}
