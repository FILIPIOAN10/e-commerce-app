package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Our copy of a Stripe dispute (chargeback). Opened by
 * {@code charge.dispute.created}, walked through {@link DisputeStatus} by later
 * {@code charge.dispute.updated} / {@code .closed} events. The status transition
 * is machine-checked ({@link DisputeStatus#canTransitionTo}); an illegal one from
 * an out-of-order webhook is logged and dropped rather than applied, so a closed
 * dispute never reopens.
 */
@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "stripe_dispute_id", nullable = false)
    @ToString.Include
    private String stripeDisputeId;

    @Column(name = "payment_intent_id", nullable = false)
    private String paymentIntentId;

    @Column(name = "charge_id")
    private String chargeId;

    /** The order the disputed charge paid for; {@code null} if it could not be resolved. */
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    /** Stripe's reason string (fraudulent, product_not_received, ...). */
    @Column(name = "reason", length = 64)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ToString.Include
    private DisputeStatus status = DisputeStatus.NEEDS_RESPONSE;

    /** Stripe's own raw status string, kept for reconciliation. */
    @Column(name = "stripe_status", length = 40)
    private String stripeStatus;

    @Column(name = "evidence_due_by")
    private LocalDateTime evidenceDueBy;

    @Column(name = "evidence_submitted_at")
    private LocalDateTime evidenceSubmittedAt;

    @Column(name = "outcome_note", columnDefinition = "TEXT")
    private String outcomeNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Dispute openedFrom(String stripeDisputeId, String paymentIntentId, String chargeId,
                                     Long orderId, BigDecimal amount, String currency, String reason,
                                     String stripeStatus, LocalDateTime evidenceDueBy) {
        Dispute d = new Dispute();
        d.stripeDisputeId = stripeDisputeId;
        d.paymentIntentId = paymentIntentId;
        d.chargeId = chargeId;
        d.orderId = orderId;
        d.amount = amount;
        d.currency = currency != null ? currency.toUpperCase() : "USD";
        d.reason = reason;
        d.stripeStatus = stripeStatus;
        d.status = DisputeStatus.fromStripe(stripeStatus);
        d.evidenceDueBy = evidenceDueBy;
        return d;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    /**
     * Move to {@code target} if the state machine allows it. Returns whether the
     * status actually changed; a no-op (same status, or an illegal jump) returns
     * {@code false} and leaves the row alone.
     */
    public boolean transitionTo(DisputeStatus target, String rawStripeStatus) {
        this.stripeStatus = rawStripeStatus;
        if (this.status == target || !this.status.canTransitionTo(target)) {
            return false;
        }
        this.status = target;
        return true;
    }

    public void markEvidenceSubmitted(LocalDateTime when) {
        if (this.evidenceSubmittedAt == null) {
            this.evidenceSubmittedAt = when;
        }
    }
}
