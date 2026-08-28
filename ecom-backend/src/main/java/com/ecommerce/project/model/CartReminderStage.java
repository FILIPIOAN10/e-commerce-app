package com.ecommerce.project.model;

import java.time.Duration;

/**
 * The escalating stages of abandoned-cart outreach. Each fires once the cart has
 * been inactive for at least {@link #getInactivityThreshold()} and no earlier
 * stage's window has been re-opened by fresh activity.
 */
public enum CartReminderStage {

    FIRST(Duration.ofHours(1)),
    SECOND(Duration.ofHours(24)),
    FINAL(Duration.ofHours(72));

    private final Duration inactivityThreshold;

    CartReminderStage(Duration inactivityThreshold) {
        this.inactivityThreshold = inactivityThreshold;
    }

    public Duration getInactivityThreshold() {
        return inactivityThreshold;
    }
}
