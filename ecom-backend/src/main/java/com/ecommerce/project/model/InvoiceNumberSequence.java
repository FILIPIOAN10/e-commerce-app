package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row per fiscal year holding the last invoice sequence number handed out for
 * that year. Read with a pessimistic write lock and incremented inside the
 * issuing transaction, so a rolled-back checkout returns its number to the pool
 * and committed invoices stay gapless.
 *
 * <p>No {@code @Version} on purpose: the row lock is the concurrency control, and
 * layering optimistic locking on top would only add spurious 409s.
 */
@Entity
@Table(name = "invoice_number_sequences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceNumberSequence {

    @Id
    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "last_value", nullable = false)
    private Long lastValue;
}
