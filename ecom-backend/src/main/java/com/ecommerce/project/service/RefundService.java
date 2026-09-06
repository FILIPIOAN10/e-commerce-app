package com.ecommerce.project.service;

import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.ReturnRequest;
import com.stripe.model.Charge;

public interface RefundService {

    /**
     * Called from inside the "mark refunded" transaction. Records a PENDING
     * {@code Refund} and enqueues the Stripe call on the outbox — or does nothing
     * when the order was not paid by card (cash on delivery is refunded off-system).
     *
     * @throws com.ecommerce.project.exception.APIException a refund for this
     *         return has already been requested (the unique-index claim lost)
     */
    void requestRefundForReturn(ReturnRequest returnRequest, Order order);

    /**
     * Called from the {@code charge.refunded} webhook. Reconciles the local
     * {@code refunds} record with what Stripe now reports for the charge, so the
     * outbox-initiated path and a dashboard-initiated refund converge on one row.
     */
    void reconcileFromCharge(Charge charge);
}
