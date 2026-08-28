package com.ecommerce.project.service.order.listener;

import com.ecommerce.project.service.InvoiceNumberService;
import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Assigns the fiscal invoice number for a newly placed order.
 *
 * <p>Unlike the other order listeners this one runs {@code BEFORE_COMMIT} and
 * <em>not</em> {@code @Async}: the number must be handed out in the same
 * transaction that commits the order, so that a checkout which rolls back also
 * rolls back its number and the per-year sequence stays gapless. If issuance
 * fails the checkout fails with it — for a fiscal document that is the correct
 * trade ("no invoice number, no sale").
 */
@Component
public class OrderInvoiceListener {

    private final InvoiceNumberService invoiceNumberService;

    public OrderInvoiceListener(InvoiceNumberService invoiceNumberService) {
        this.invoiceNumberService = invoiceNumberService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        invoiceNumberService.issueFor(event.orderId());
    }
}
