package com.ecommerce.project.service.order;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.service.payment.PaymentAttempt;
import com.ecommerce.project.service.payment.PaymentGatewayRegistry;
import com.ecommerce.project.service.payment.PaymentVerification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The payment side of checkout: verify the reference, then persist the payment
 * record. Pulled out of {@code OrderServiceImpl} so it no longer carries the
 * payment repository and the gateway registry.
 */
@Component
@RequiredArgsConstructor
public class OrderPaymentHandler {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRegistry paymentGatewayRegistry;

    /**
     * Fails fast if a payment reference is present but unusable: already spent on
     * another order, or not confirmed by its gateway for the expected amount. A
     * reference-less attempt (e.g. cash on delivery) is a no-op.
     */
    public void verify(String paymentMethod, String pgName, String pgPaymentId, double expectedTotal) {
        PaymentAttempt attempt = new PaymentAttempt(paymentMethod, pgName, pgPaymentId, expectedTotal);
        if (!attempt.hasReference()) {
            return;
        }

        // Gateway-agnostic: a payment reference may back exactly one order.
        paymentRepository.findByPgPaymentId(pgPaymentId).ifPresent(existing -> {
            throw new APIException("This payment has already been used for order "
                    + existing.getOrder().getId());
        });

        // Gateway-specific: confirm the payment succeeded for the expected amount.
        paymentGatewayRegistry.select(attempt).ifPresent(gateway -> {
            PaymentVerification result = gateway.verify(attempt);
            if (!result.verified()) {
                throw new APIException(result.reason());
            }
        });
    }

    /** Persists the payment record for an order. */
    public Payment record(Order order, String paymentMethod, String pgPaymentId,
                          String pgStatus, String pgResponseMessage, String pgName) {
        Payment payment = new Payment(paymentMethod, pgPaymentId, pgStatus, pgResponseMessage, pgName);
        payment.setOrder(order);
        return paymentRepository.save(payment);
    }
}
