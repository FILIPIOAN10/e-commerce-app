package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promo_campaigns")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "campaign_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    // A percentage, carried as NUMERIC(12,2) like products.discount (V25) so it
    // multiplies into a price without float error. See V30.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Boolean active = true;

    /**
     * Whether this campaign's prices are currently pushed onto its products.
     *
     * <p>Distinct from {@link #active}, which is the operator's intent. This is
     * the sweep's own record of what it has done, so a campaign is applied once
     * when it starts and reverted once when it stops — rather than every product
     * being rewritten on every pass, and never put back.
     */
    @Column(nullable = false)
    private Boolean applied = false;
}
