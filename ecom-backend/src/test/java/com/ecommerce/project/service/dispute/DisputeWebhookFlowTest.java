package com.ecommerce.project.service.dispute;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.Dispute;
import com.ecommerce.project.model.DisputeStatus;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OutboxStatus;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.payload.DisputeDTO;
import com.ecommerce.project.payload.DisputeEvidenceFileDTO;
import com.ecommerce.project.repository.DisputeRepository;
import com.ecommerce.project.repository.OutboxEventRepository;
import com.ecommerce.project.service.DisputeEvidenceService;
import com.ecommerce.project.service.DisputeService;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxProcessor;
import com.stripe.model.Dispute.EvidenceDetails;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * B4 end to end against real Postgres: a Stripe dispute event opens a
 * {@code disputes} row and an outbox event in one transaction; draining the
 * outbox alerts the admins; a later "lost" event walks the row to its terminal
 * state and fires the closed alert; an admin can attach and read back evidence.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DisputeWebhookFlowTest {

    @Autowired private DisputeService disputeService;
    @Autowired private DisputeEvidenceService disputeEvidenceService;
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxProcessor outboxProcessor;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    @MockitoBean private NotificationService notificationService;

    private final String tag = "dsp" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;
    private Long orderId;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(txManager);
        orderId = tx.execute(status -> {
            Payment payment = new Payment("STRIPE", "pi_" + tag, "succeeded", "ok", "Stripe");
            entityManager.persist(payment);

            Order order = new Order();
            order.setEmail(tag + "@example.com");
            order.setOrderDate(LocalDate.now());
            order.setOrderStatus("Delivered");
            order.setTotalAmount(new BigDecimal("84.99"));
            order.setPayment(payment);
            entityManager.persist(order);
            return order.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "DELETE FROM outbox_event WHERE event_type IN ('DISPUTE_OPENED','DISPUTE_CLOSED') "
                    + "AND payload IN (SELECT '{\"disputeId\":' || id || '}' FROM disputes WHERE payment_intent_id = :pi)")
                    .setParameter("pi", "pi_" + tag).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM dispute_evidence_files WHERE dispute_id IN "
                    + "(SELECT id FROM disputes WHERE payment_intent_id = :pi)")
                    .setParameter("pi", "pi_" + tag).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM disputes WHERE payment_intent_id = :pi")
                    .setParameter("pi", "pi_" + tag).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM orders WHERE email LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM payments WHERE pg_payment_id = :pi")
                    .setParameter("pi", "pi_" + tag).executeUpdate();
        });
    }

    private com.stripe.model.Dispute stripeDispute(String stripeStatus, long submissionCount) {
        com.stripe.model.Dispute d = Mockito.mock(com.stripe.model.Dispute.class);
        EvidenceDetails details = Mockito.mock(EvidenceDetails.class);
        Mockito.when(d.getId()).thenReturn("dp_" + tag);
        Mockito.when(d.getPaymentIntent()).thenReturn("pi_" + tag);
        Mockito.when(d.getCharge()).thenReturn("ch_" + tag);
        Mockito.when(d.getAmount()).thenReturn(8499L);
        Mockito.when(d.getCurrency()).thenReturn("usd");
        Mockito.when(d.getReason()).thenReturn("fraudulent");
        Mockito.when(d.getStatus()).thenReturn(stripeStatus);
        Mockito.when(d.getEvidenceDetails()).thenReturn(details);
        Mockito.when(details.getDueBy()).thenReturn(1_900_000_000L);
        Mockito.when(details.getSubmissionCount()).thenReturn(submissionCount);
        return d;
    }

    @Test
    @DisplayName("created → row + outbox alert; lost → terminal + closed alert; evidence round-trips")
    void fullDisputeLifecycle() {
        disputeService.openFromStripe(stripeDispute("needs_response", 0));

        Dispute opened = tx.execute(s ->
                disputeRepository.findByStripeDisputeId("dp_" + tag).orElseThrow());
        assertThat(opened.getStatus()).isEqualTo(DisputeStatus.NEEDS_RESPONSE);
        assertThat(opened.getOrderId()).isEqualTo(orderId);
        assertThat(opened.getAmount()).isEqualByComparingTo("84.99");
        assertThat(outboxEventRepository.findAll().stream().anyMatch(e ->
                e.getEventType().equals(OutboxEventTypes.DISPUTE_OPENED)
                        && e.getStatus() == OutboxStatus.PENDING)).isTrue();

        outboxProcessor.processBatch();
        verify(notificationService).notifyAdmins(eq("Chargeback opened"), any(), eq("DISPUTE_OPENED"), eq(orderId));

        // A redelivered "created" is folded into an update, not a second open.
        disputeService.openFromStripe(stripeDispute("needs_response", 0));
        long rowCount = tx.execute(s -> disputeRepository.findAll().stream()
                .filter(d -> d.getStripeDisputeId().equals("dp_" + tag)).count());
        assertThat(rowCount).isEqualTo(1L);

        // Evidence upload + download.
        DisputeEvidenceFileDTO uploaded = disputeEvidenceService.attach(opened.getId(),
                new MockMultipartFile("file", "receipt.pdf", "application/pdf", "PDFBYTES".getBytes()),
                "admin@example.com");
        DisputeEvidenceService.EvidenceDownload dl =
                disputeEvidenceService.download(opened.getId(), uploaded.id());
        assertThat(dl.filename()).isEqualTo("receipt.pdf");
        assertThat(new String(dl.bytes())).isEqualTo("PDFBYTES");

        // charge.dispute.closed → lost.
        disputeService.closeFromStripe(stripeDispute("lost", 1));

        Dispute closed = tx.execute(s -> disputeRepository.findById(opened.getId()).orElseThrow());
        assertThat(closed.getStatus()).isEqualTo(DisputeStatus.LOST);
        assertThat(closed.isTerminal()).isTrue();
        assertThat(closed.getOutcomeNote()).isNotBlank();
        assertThat(closed.getEvidenceSubmittedAt()).isNotNull();

        outboxProcessor.processBatch();
        verify(notificationService).notifyAdmins(eq("Chargeback lost"), any(), eq("DISPUTE_CLOSED"), eq(orderId));

        // Detail view carries the evidence file.
        DisputeDTO detail = disputeService.get(opened.getId());
        assertThat(detail.status()).isEqualTo("LOST");
        assertThat(detail.evidenceFiles()).extracting(DisputeEvidenceFileDTO::originalName)
                .containsExactly("receipt.pdf");
    }
}
