package com.ecommerce.project.service.subscription;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.model.OutboxStatus;
import com.ecommerce.project.model.SubscriptionPlan;
import com.ecommerce.project.model.UserSubscription;
import com.ecommerce.project.repository.OutboxEventRepository;
import com.ecommerce.project.repository.UserSubscriptionRepository;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.OutboxProcessor;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * B2 end to end against real Postgres: a failed renewal marks the subscription
 * PAST_DUE and writes a dunning outbox row in one transaction; draining the
 * outbox delivers the notice exactly once.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionLifecycleFlowTest {

    @Autowired private SubscriptionLifecycleService lifecycle;
    @Autowired private UserSubscriptionRepository userSubscriptionRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxProcessor outboxProcessor;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    @MockitoBean private EmailService emailService;
    @MockitoBean private NotificationService notificationService;

    private final String tag = "sub" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;
    private final String subscriptionId = "sub_flow_";
    private Long subRowId;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(txManager);
        subRowId = tx.execute(status -> {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setName("Pro " + tag);
            plan.setAmount(new java.math.BigDecimal("9.99"));
            plan.setInterval("month");
            entityManager.persist(plan);

            UserSubscription sub = new UserSubscription();
            sub.setEmail(tag + "@example.com");
            sub.setPlan(plan);
            sub.setStripeSubscriptionId(subscriptionId + tag);
            sub.setStatus(SubscriptionStatus.ACTIVE.name());
            sub.setCurrentPeriodEnd(LocalDateTime.now().plusDays(2));
            entityManager.persist(sub);
            return sub.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "DELETE FROM outbox_event WHERE payload LIKE :p")
                    .setParameter("p", "%" + tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM user_subscriptions WHERE email LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM subscription_plans WHERE name LIKE :t")
                    .setParameter("t", "Pro " + tag + "%").executeUpdate();
        });
    }

    @Test
    @DisplayName("payment failed → PAST_DUE + one dunning outbox row; drain delivers once")
    void dunningNoticeIsDeliveredOnceThroughTheOutbox() {
        lifecycle.paymentFailed(subscriptionId + tag, null);

        UserSubscription pastDue = tx.execute(s ->
                userSubscriptionRepository.findById(subRowId).orElseThrow());
        assertThat(pastDue.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE.name());

        assertThat(outboxEventRepository.findAll().stream().anyMatch(e ->
                e.getEventType().equals(OutboxEventTypes.SUBSCRIPTION_PAYMENT_FAILED)
                        && e.getStatus() == OutboxStatus.PENDING
                        && e.getPayload().contains(tag)))
                .isTrue();

        outboxProcessor.processBatch();

        verify(emailService).sendSubscriptionPaymentFailedEmail(eq(tag + "@example.com"), eq("Pro " + tag));
        verify(notificationService).notifyUser(eq(tag + "@example.com"),
                eq("Subscription payment failed"), org.mockito.ArgumentMatchers.anyString(),
                eq("SUBSCRIPTION_PAST_DUE"));

        assertThat(outboxEventRepository.findAll().stream()
                .filter(e -> e.getPayload().contains(tag))
                .allMatch(e -> e.getStatus() == OutboxStatus.DONE))
                .isTrue();

        int claimed = outboxProcessor.processBatch();
        assertThat(claimed).isZero();
    }
}
