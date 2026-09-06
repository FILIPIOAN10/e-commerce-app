package com.ecommerce.project.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long planId;

    @NotBlank
    private String name;

    private String description;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private String stripeProductId;
    private String stripePriceId;

    @NotBlank
    private String interval;

    // The recurring price. NUMERIC(12,2), and the cents figure sent to Stripe is
    // taken with Money.toCents() rather than (long)(amount * 100). See V30.
    @Positive
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private String currency = "USD";

    private Boolean active = true;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
