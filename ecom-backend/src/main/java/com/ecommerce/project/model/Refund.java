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

/**
 * One refund against a paid order. Created {@code PENDING} inside the transaction
 * that marks a return refunded; driven to {@code SUCCEEDED} / {@code FAILED} by
 * {@code RefundHandler} (the outbox side effect that calls Stripe) and, as a
 * backstop, by the {@code charge.refunded} webhook.
 *
 * <p>Two unique indexes carry the correctness:
 * {@code uk_refunds_return} (partial) makes "issue the refund" idempotent per
 * return, and {@code uk_refunds_stripe_id} (partial) means the outbox path and
 * the webhook path cannot both record the same Stripe refund. See {@code V31}.
 */
@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    /** The return this refund settles. {@code null} for a refund made directly in
     *  the Stripe dashboard and only reconciled back here by the webhook. */
    @Column(name = "return_id")
    private Long returnId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_intent_id", nullable = false)
    private String paymentIntentId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ToString.Include
    private RefundStatus status = RefundStatus.PENDING;

    /** Set once Stripe has accepted the refund — from the API response on the
     *  outbox path, from the event on the webhook path. Unique (partial). */
    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Refund pendingFor(Long returnId, Long orderId, String paymentIntentId, BigDecimal amount) {
        Refund r = new Refund();
        r.returnId = returnId;
        r.orderId = orderId;
        r.paymentIntentId = paymentIntentId;
        r.amount = amount;
        r.status = RefundStatus.PENDING;
        return r;
    }

    public void markSucceeded(String stripeRefundId) {
        this.stripeRefundId = stripeRefundId;
        this.status = RefundStatus.SUCCEEDED;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = RefundStatus.FAILED;
        this.failureReason = reason;
    }
}
