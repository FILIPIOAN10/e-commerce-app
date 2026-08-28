package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Records that one abandoned-cart reminder stage was sent for a cart. The
 * {@code (cart_id, stage)} unique constraint is the idempotency key: the sweep
 * inserts this row <em>before</em> enqueuing the email, so a concurrent or
 * repeated sweep cannot double-send a stage.
 */
@Entity
@Table(name = "cart_reminder", uniqueConstraints =
        @UniqueConstraint(name = "uk_cart_reminder_cart_stage", columnNames = {"cart_id", "stage"}))
@Getter
@Setter
@NoArgsConstructor
public class CartReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    @ToString.Exclude
    private Cart cart;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private CartReminderStage stage;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    /** Set when the customer follows the recovery link. Null until then. */
    @Column(name = "recovered_at")
    private Instant recoveredAt;

    public CartReminder(Cart cart, CartReminderStage stage) {
        this.cart = cart;
        this.stage = stage;
    }
}
