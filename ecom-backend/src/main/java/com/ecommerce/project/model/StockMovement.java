package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One entry in the stock ledger: what moved, by how much, why, and what the
 * balance became.
 *
 * <p>Append-only. Nothing here is ever updated or deleted — a correction is
 * another movement, which is what makes the history readable after the fact.
 *
 * <p>Holds {@code productId} as a plain column rather than a {@code @ManyToOne}:
 * movements are written on the checkout hot path and read as a flat list, and
 * neither needs a {@code Product} materialised.
 */
@Entity
@Table(name = "stock_movement", indexes = {
        @Index(name = "idx_stock_movement_product", columnList = "product_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Signed: negative consumes stock, positive returns it. Never zero. */
    @Column(name = "delta", nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private StockMovementReason reason;

    /** What caused it — {@code ORDER}, {@code CART}, {@code ADMIN_EDIT}, … */
    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    /** {@code products.quantity} immediately after this movement was applied. */
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "note", length = 255)
    private String note;

    /** Username of whoever caused it, or {@code system} / {@code guest}. */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static StockMovement of(Long productId, int delta, StockMovementReason reason,
                                   String refType, Long refId, int balanceAfter,
                                   String note, String createdBy) {
        StockMovement movement = new StockMovement();
        movement.productId = productId;
        movement.delta = delta;
        movement.reason = reason;
        movement.refType = refType;
        movement.refId = refId;
        movement.balanceAfter = balanceAfter;
        movement.note = note;
        movement.createdBy = createdBy;
        return movement;
    }
}
