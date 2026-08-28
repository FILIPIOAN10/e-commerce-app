package com.ecommerce.project.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "carts")
@NoArgsConstructor
@AllArgsConstructor
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cartId;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;


    @OneToMany(mappedBy = "cart",cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},orphanRemoval = true)
    private List<CartItem> cartItems = new ArrayList<>();

    private Double totalPrice=0.0;

    /**
     * When the owner last acted on this cart. Stamped explicitly by the
     * user-facing mutators in {@code CartServiceImpl} (not by system price
     * syncs), and defaulted on create. Drives abandoned-cart detection.
     */
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @PrePersist
    void initLastActivityAt() {
        if (lastActivityAt == null) {
            lastActivityAt = Instant.now();
        }
    }
}
