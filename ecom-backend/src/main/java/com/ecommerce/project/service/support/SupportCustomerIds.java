package com.ecommerce.project.service.support;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * How a shop user is named on the SupportPilot side: {@code u-<userId>}.
 *
 * <p>Not the email: SupportPilot only accepts {@code [A-Za-z0-9_-]{1,64}}, and an
 * opaque id means the customer's email never reaches the agent service or, from
 * there, the model provider.
 */
public final class SupportCustomerIds {

    private static final Pattern FORMAT = Pattern.compile("^u-(\\d{1,18})$");

    private SupportCustomerIds() {
    }

    public static String of(Long userId) {
        return "u-" + userId;
    }

    public static Optional<Long> userIdOf(String customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        var matcher = FORMAT.matcher(customerId);
        return matcher.matches() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
    }
}
