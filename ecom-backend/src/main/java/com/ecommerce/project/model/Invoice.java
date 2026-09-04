package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A fiscal invoice issued for a committed order. The pair
 * {@code (fiscalYear, sequenceNo)} is a gapless-per-year counter; the human-facing
 * {@link #invoiceNumber} is its formatted form. {@code order_id} is unique, so an
 * order has at most one invoice and issuance is naturally idempotent.
 *
 * @see com.ecommerce.project.service.InvoiceNumberService
 */
@Entity
@Table(name = "invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invoice_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_invoice_year_seq", columnNames = {"fiscal_year", "sequence_no"}),
        @UniqueConstraint(name = "uk_invoice_number", columnNames = "invoice_number")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    @ToString.Exclude
    private Order order;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;
}
