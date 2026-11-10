package com.ecommerce.project.service;

import com.ecommerce.project.payload.StripePaymentDto;
import com.ecommerce.project.service.payment.RefundResult;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

public interface StripeService {

    /**
     * The one currency the shop charges in. Every order total is computed in
     * USD and sent to Stripe as USD cents; the customer's display currency is
     * only frozen onto the order for invoices. The charge currency is therefore
     * never the client's to choose: 8900 means $89.00, not ¥8,900.
     */
    String SETTLEMENT_CURRENCY = "usd";
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
