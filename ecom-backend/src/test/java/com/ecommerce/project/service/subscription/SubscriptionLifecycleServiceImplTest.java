package com.ecommerce.project.service.subscription;

import com.ecommerce.project.model.SubscriptionPlan;
import com.ecommerce.project.model.UserSubscription;
import com.ecommerce.project.repository.UserSubscriptionRepository;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.SubscriptionNoticeOutboxPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleServiceImplTest {

    @Mock private UserSubscriptionRepository repository;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks private SubscriptionLifecycleServiceImpl service;

    private static long epoch(LocalDateTime when) {
        return when.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private UserSubscription subscription(String status) {
        UserSubscription sub = new UserSubscription();
        sub.setEmail("buyer@example.com");
        sub.setStripeSubscriptionId("sub_123");
        sub.setStripeCheckoutSessionId("cs_123");
        sub.setStatus(status);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("Pro");
        sub.setPlan(plan);
        return sub;
    }

    @Test
    void activateFromCheckout_bindsSubscriptionIdAndGoesActive() {
        UserSubscription sub = subscription(SubscriptionStatus.PENDING.name());
        when(repository.findByStripeCheckoutSessionId("cs_123")).thenReturn(Optional.of(sub));
        LocalDateTime end = LocalDateTime.now().plusMonths(1).withNano(0);

        service.activateFromCheckout("cs_123", "sub_new", epoch(end.minusMonths(1)), epoch(end));

        assertThat(sub.getStripeSubscriptionId()).isEqualTo("sub_new");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(end);
        verify(repository).save(sub);
    }

    @Test
    void activateFromCheckout_unknownSession_isANoOp() {
        when(repository.findByStripeCheckoutSessionId("cs_missing")).thenReturn(Optional.empty());

        service.activateFromCheckout("cs_missing", "sub_x", null, null);

        verify(repository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void renewed_extendsPeriodAndClearsDunning() {
        UserSubscription sub = subscription(SubscriptionStatus.PAST_DUE.name());
        when(repository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(sub));
        LocalDateTime end = LocalDateTime.now().plusMonths(1).withNano(0);

        service.renewed("sub_123", epoch(end));

        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(end);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
        verify(repository).save(sub);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void paymentFailed_marksPastDueAndEnqueuesNotice() {
        UserSubscription sub = subscription(SubscriptionStatus.ACTIVE.name());
        when(repository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(sub));

        service.paymentFailed("sub_123", null);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE.name());
        verify(outboxEventPublisher).publish(eq(OutboxEventTypes.SUBSCRIPTION_PAYMENT_FAILED),
                eq(new SubscriptionNoticeOutboxPayload("buyer@example.com", "Pro")));
    }

    @Test
    void paymentFailed_onCanceledSubscription_doesNothing() {
        UserSubscription sub = subscription(SubscriptionStatus.CANCELED.name());
        when(repository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(sub));

        service.paymentFailed("sub_123", null);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELED.name());
        verify(repository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void syncFromStripe_newlyEnded_setsCanceledAtAndPublishesOnce() {
        UserSubscription sub = subscription(SubscriptionStatus.ACTIVE.name());
        when(repository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(sub));

        service.syncFromStripe("sub_123", SubscriptionStatus.CANCELED, null);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELED.name());
        assertThat(sub.getCanceledAt()).isNotNull();
        verify(outboxEventPublisher).publish(eq(OutboxEventTypes.SUBSCRIPTION_ENDED), any());
    }

    @Test
    void syncFromStripe_alreadyEnded_doesNotRepublish() {
        UserSubscription sub = subscription(SubscriptionStatus.CANCELED.name());
        sub.setCanceledAt(LocalDateTime.now().minusDays(3));
        when(repository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(sub));

        service.syncFromStripe("sub_123", SubscriptionStatus.UNPAID, null);

        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void ended_cancelsAndPublishesEndedNoticeOnce() {
        UserSubscription sub = subscription(SubscriptionStatus.ACTIVE.name());
        when(repository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(sub));

        service.ended("sub_123");

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELED.name());
        assertThat(sub.getCanceledAt()).isNotNull();
        verify(outboxEventPublisher).publish(eq(OutboxEventTypes.SUBSCRIPTION_ENDED),
                eq(new SubscriptionNoticeOutboxPayload("buyer@example.com", "Pro")));
    }

    @Test
    void ended_whenAlreadyEnded_isSilent() {
        UserSubscription sub = subscription(SubscriptionStatus.INCOMPLETE_EXPIRED.name());
        when(repository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.of(sub));

        service.ended("sub_123");

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELED.name());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void unknownSubscriptionId_isIgnored() {
        when(repository.findByStripeSubscriptionId("sub_ghost")).thenReturn(Optional.empty());

        service.renewed("sub_ghost", 123L);
        service.ended("sub_ghost");
        service.paymentFailed("sub_ghost", null);

        verify(repository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }
}
