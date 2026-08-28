package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private Integer discountPercent;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false)
    private Integer maxUses;

    @Column(nullable = false)
    private Integer usedCount;

    @Column(nullable = false)
    private Boolean active;

    @PrePersist
    void onCreate() {
        if (usedCount == null) usedCount = 0;
        if (active == null) active = true;
    }
}
