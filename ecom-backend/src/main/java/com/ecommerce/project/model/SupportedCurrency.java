package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A currency the store offers as a presentation / checkout currency. The row set
 * is small and reference-only — seeded by {@code V32} and edited rarely — so it
 * is read through a short Redis cache rather than hit per request. {@code USD} is
 * the base and is always present and active.
 */
@Entity
@Table(name = "supported_currencies")
@Getter
@Setter
public class SupportedCurrency {

    @Id
    @Column(length = 3, nullable = false)
    private String code;

    @Column(nullable = false, length = 8)
    private String symbol;

    /** Minor-unit digits: 2 for USD/EUR, 0 for JPY. Drives conversion rounding. */
    @Column(name = "decimal_digits", nullable = false)
    private short decimalDigits = 2;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
