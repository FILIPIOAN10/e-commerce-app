package com.ecommerce.project.service.refund;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OutboxStatus;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.Refund;
import com.ecommerce.project.model.RefundStatus;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.repository.OutboxEventRepository;
import com.ecommerce.project.repository.RefundRepository;
import com.ecommerce.project.service.ReturnService;
import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.service.outbox.OutboxProcessor;
import com.ecommerce.project.service.payment.RefundResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B1 end to end: an admin marking a card return refunded records a PENDING
 * refund and an outbox event in one transaction; the outbox handler issues the
 * Stripe refund exactly once and drives the row to SUCCEEDED; a redelivery is a
 * no-op.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AutomatedRefundFlowTest {

    @Autowired private ReturnService returnService;
    @Autowired private RefundRepository refundRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxProcessor outboxProcessor;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    @MockitoBean private StripeService stripeService;

    private final String tag = "rf" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;
    private Long returnId;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(txManager);
        returnId = tx.execute(status -> {
            Payment payment = new Payment("STRIPE", "pi_" + tag, "succeeded", "ok", "Stripe");
            entityManager.persist(payment);

            Order order = new Order();
            order.setEmail(tag + "@example.com");
            order.setOrderDate(java.time.LocalDate.now());
            order.setOrderStatus("Returned");
            order.setTotalAmount(new BigDecimal("84.99"));
            order.setPayment(payment);
            entityManager.persist(order);

            ReturnRequest rr = new ReturnRequest();
            rr.setOrderId(order.getId());
            rr.setUserEmail(order.getEmail());
            rr.setReason("defective");
            rr.setStatus("SHIPPED_BACK");
            rr.setRequestedAt(LocalDateTime.now());
            rr.setRefundAmount(order.getTotalAmount());
            entityManager.persist(rr);
            return rr.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            // Outbox rows first, matched precisely to this test's refunds.
            tagged("DELETE FROM outbox_event WHERE event_type = 'REFUND_REQUESTED' AND payload IN ("
                   + "  SELECT '{\"refundId\":' || id || '}' FROM refunds"
                   + "  WHERE order_id IN (SELECT id FROM orders WHERE email LIKE :t))");
            tagged("DELETE FROM refunds WHERE order_id IN (SELECT id FROM orders WHERE email LIKE :t)");
            tagged("DELETE FROM return_requests WHERE user_email LIKE :t");
            tagged("DELETE FROM orders WHERE email LIKE :t");
            entityManager.createNativeQuery("DELETE FROM payments WHERE pg_payment_id = :pi")
                    .setParameter("pi", "pi_" + tag).executeUpdate();
        });
    }

    private void tagged(String sql) {
        entityManager.createNativeQuery(sql).setParameter("t", tag + "%").executeUpdate();
    }

    @Test
    @DisplayName("mark refunded → PENDING refund + outbox event; processing → SUCCEEDED, once")
    void refundIsIssuedOnceThroughTheOutbox() throws Exception {
        when(stripeService.issueRefund(eq("pi_" + tag), anyLong(), anyString()))
                .thenReturn(new RefundResult("re_" + tag, "succeeded"));

        returnService.markAsRefunded(returnId);

        Refund refund = tx.execute(s -> refundRepository.findByReturnId(returnId).orElseThrow());
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getAmount()).isEqualByComparingTo("84.99");
        assertThat(refund.getPaymentIntentId()).isEqualTo("pi_" + tag);
        assertThat(outboxEventRepository.findAll().stream()
                .anyMatch(e -> e.getEventType().equals("REFUND_REQUESTED") && e.getStatus() == OutboxStatus.PENDING))
                .isTrue();

        // First drain: the handler calls Stripe and settles the row.
        outboxProcessor.processBatch();

        Refund settled = tx.execute(s -> refundRepository.findById(refund.getId()).orElseThrow());
        assertThat(settled.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(settled.getStripeRefundId()).isEqualTo("re_" + tag);

        // Second drain: nothing left; no second Stripe call.
        int claimed = outboxProcessor.processBatch();
        assertThat(claimed).isZero();
        verify(stripeService, times(1)).issueRefund(eq("pi_" + tag), eq(8499L), eq("refund:" + refund.getId()));

        // Return and order both reflect the refund.
        ReturnRequest rr = tx.execute(s -> entityManager.find(ReturnRequest.class, returnId));
        assertThat(rr.getStatus()).isEqualTo("REFUNDED");
        Order order = tx.execute(s -> entityManager.createQuery(
                "select o from Order o where o.id = :id", Order.class)
                .setParameter("id", rr.getOrderId()).getSingleResult());
        assertThat(order.getOrderStatus()).isEqualTo("Refunded");
    }
}
