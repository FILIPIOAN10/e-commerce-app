package com.ecommerce.project.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * The lifecycle of a {@link Dispute}, as an explicit state machine — the same
 * shape as {@code OrderStatus}. Stripe has around ten dispute status strings;
 * {@link #fromStripe(String)} folds them into these five, and each constant
 * declares which transitions are legal so a malformed or out-of-order webhook
 * cannot walk a closed dispute back to open.
 *
 * <ul>
 *   <li>{@code NEEDS_RESPONSE} — opened; we owe evidence by {@code evidence_due_by}.</li>
 *   <li>{@code UNDER_REVIEW} — evidence submitted (by us or auto), bank deciding.</li>
 *   <li>{@code WON} — resolved in our favour. Terminal.</li>
 *   <li>{@code LOST} — resolved against us; funds withdrawn. Terminal.</li>
 *   <li>{@code CLOSED} — closed with no win/loss outcome (an inquiry withdrawn,
 *       a warning that never became a formal dispute). Terminal.</li>
 * </ul>
 */
public enum DisputeStatus {

    NEEDS_RESPONSE("UNDER_REVIEW", "WON", "LOST", "CLOSED"),
    UNDER_REVIEW("WON", "LOST", "CLOSED"),
    WON(),
    LOST(),
    CLOSED();

    private final Set<String> allowedNext;

    DisputeStatus(String... allowedNext) {
        this.allowedNext = Set.of(allowedNext);
    }

    public boolean isTerminal() {
        return allowedNext.isEmpty();
    }

    /** Same status is always allowed (an idempotent re-sync); otherwise the graph decides. */
    public boolean canTransitionTo(DisputeStatus target) {
        return this == target || allowedNext.contains(target.name());
    }

    /** Map a Stripe dispute {@code status} onto our machine. Unknown → NEEDS_RESPONSE (safest: keeps it on the radar). */
    public static DisputeStatus fromStripe(String stripeStatus) {
        return switch (stripeStatus == null ? "" : stripeStatus) {
            case "warning_needs_response", "needs_response" -> NEEDS_RESPONSE;
            case "warning_under_review", "under_review" -> UNDER_REVIEW;
            case "won" -> WON;
            case "lost" -> LOST;
            case "warning_closed", "charge_refunded" -> CLOSED;
            default -> NEEDS_RESPONSE;
        };
    }

    public static Optional<DisputeStatus> of(String name) {
        return Arrays.stream(values()).filter(s -> s.name().equals(name)).findFirst();
    }
}
