package com.ecommerce.project.service;

import com.ecommerce.project.payload.StripePaymentDto;
import com.ecommerce.project.service.payment.RefundResult;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

public interface StripeService {
    PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException;

    PaymentIntent retrievePaymentIntent(String paymentIntentId);

    /**
     * Refunds {@code amountMinorUnits} (cents) against a PaymentIntent.
     * {@code idempotencyKey} is handed to Stripe so a retried call returns the
     * original refund instead of creating a second one.
     *
     * @throws com.stripe.exception.InvalidRequestException Stripe rejected the
     *         request for a reason retrying will not fix (already refunded, not
     *         refundable)
     * @throws StripeException a transient failure — the caller should retry
     */
    RefundResult issueRefund(String paymentIntentId, long amountMinorUnits, String idempotencyKey)
            throws StripeException;
}
