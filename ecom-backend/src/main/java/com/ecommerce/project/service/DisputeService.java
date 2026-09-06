package com.ecommerce.project.service;

import com.ecommerce.project.payload.DisputeDTO;
import com.stripe.model.Dispute;

import java.util.List;

/**
 * Keeps the local {@code disputes} table in step with Stripe's chargeback
 * lifecycle. The three {@code *FromStripe} methods are the webhook's entry
 * points; the read methods back the admin screen.
 */
public interface DisputeService {

    /** {@code charge.dispute.created} — record a new dispute (or fold a replayed create into an update). */
    void openFromStripe(Dispute stripeDispute);

    /** {@code charge.dispute.updated} — mirror status / evidence changes onto the local row. */
    void syncFromStripe(Dispute stripeDispute);

    /** {@code charge.dispute.closed} — record the final outcome (won / lost / closed). */
    void closeFromStripe(Dispute stripeDispute);

    List<DisputeDTO> listAll();

    DisputeDTO get(Long disputeId);
}
