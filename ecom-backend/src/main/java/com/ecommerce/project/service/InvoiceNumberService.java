package com.ecommerce.project.service;

import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Invoice;
import com.ecommerce.project.model.InvoiceNumberSequence;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.repository.InvoiceNumberSequenceRepository;
import com.ecommerce.project.repository.InvoiceRepository;
import com.ecommerce.project.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Issues gapless fiscal invoice numbers.
 *
 * <p>A fiscal invoice number may not have holes within its year, so a database
 * SEQUENCE is not usable (a rollback would still consume the value). Instead a
 * per-year counter row is incremented <em>inside the same transaction</em> that
 * inserts the invoice — from the checkout transaction when an order is placed
 * ({@link com.ecommerce.project.service.order.listener.OrderInvoiceListener}),
 * or in its own transaction when a PDF is requested for a pre-existing order.
 * Roll the transaction back and the increment rolls back with it, so every
 * committed invoice maps to exactly one consumed number and committed invoices
 * form an unbroken run {@code 1..N} per year.
 *
 * <p>Concurrent issuers serialise on the counter row ({@code SELECT ... FOR
 * UPDATE}). Issuance happens at the tail of checkout, so a slow checkout briefly
 * holds that lock; at high volume the assignment would move behind the
 * transactional outbox. For this store the serialisation is not a concern.
 *
 * <p>Issuance is idempotent: {@code order_id} is unique on {@code invoices}, and
 * {@link #issueFor(Long)} returns the existing row for an order that already has
 * one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberSequenceRepository sequenceRepository;
    private final OrderRepository orderRepository;

    /** Optional series prefix, e.g. {@code "INV-"}. Empty by default. */
    @Value("${invoice.number.prefix:}")
    private String numberPrefix;

    /**
     * Returns the invoice for {@code orderId}, issuing one on first call. Safe to
     * call repeatedly and from more than one path for the same order.
     */
    @Transactional
    public Invoice issueFor(Long orderId) {
        return invoiceRepository.findByOrderId(orderId)
                .orElseGet(() -> issueNew(orderId));
    }

    private Invoice issueNew(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        int fiscalYear = fiscalYearOf(order);

        sequenceRepository.insertIfAbsent(fiscalYear);
        InvoiceNumberSequence sequence = sequenceRepository.findByFiscalYearForUpdate(fiscalYear)
                .orElseThrow(() -> new IllegalStateException(
                        "Invoice number sequence row missing for fiscal year " + fiscalYear));

        long next = sequence.getLastValue() + 1L;
        sequence.setLastValue(next);
        sequenceRepository.save(sequence);

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setFiscalYear(fiscalYear);
        invoice.setSequenceNo(next);
        invoice.setInvoiceNumber(format(fiscalYear, next));
        Invoice saved = invoiceRepository.save(invoice);

        log.info("Issued invoice {} for order {}", saved.getInvoiceNumber(), orderId);
        return saved;
    }

    private int fiscalYearOf(Order order) {
        LocalDate orderDate = order.getOrderDate();
        return orderDate != null ? orderDate.getYear() : LocalDate.now().getYear();
    }

    private String format(int fiscalYear, long sequenceNo) {
        return String.format("%s%d-%06d", numberPrefix, fiscalYear, sequenceNo);
    }
}
