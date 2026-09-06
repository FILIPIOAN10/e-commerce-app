package com.ecommerce.project.service.outbox.handler;

import com.ecommerce.project.model.Refund;
import com.ecommerce.project.model.RefundStatus;
import com.ecommerce.project.repository.RefundRepository;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.service.outbox.OutboxPayloadCodec;
import com.ecommerce.project.service.payment.RefundResult;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundHandler")
class RefundHandlerTest {

    @Mock private RefundRepository refundRepository;
    @Mock private StripeService stripeService;
    @Mock private NotificationService notificationService;

    private RefundHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RefundHandler(refundRepository, stripeService, notificationService, new OutboxPayloadCodec());
    }

    private Refund pending(long id) {
        Refund r = Refund.pendingFor(7L, 3L, "pi_abc", new BigDecimal("84.99"));
        r.setId(id);
        return r;
    }

    @Test
    @DisplayName("a PENDING refund is sent to Stripe with an idempotency key and marked SUCCEEDED")
    void happyPath() throws Exception {
        Refund refund = pending(1L);
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(stripeService.issueRefund("pi_abc", 8499L, "refund:1"))
                .thenReturn(new RefundResult("re_123", "succeeded"));

        handler.handle("{\"refundId\":1}");

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(refund.getStripeRefundId()).isEqualTo("re_123");
        verify(refundRepository).save(refund);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("a redelivery after success does nothing — no second Stripe call")
    void alreadySettledIsANoOp() throws Exception {
        Refund refund = pending(1L);
        refund.markSucceeded("re_123");
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));

        handler.handle("{\"refundId\":1}");

        verify(stripeService, never()).issueRefund(anyString(), anyLong(), anyString());
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("a terminal Stripe status fails the refund and notifies an admin, without throwing")
    void terminalStatusFailsAndNotifies() throws Exception {
        Refund refund = pending(1L);
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(stripeService.issueRefund(eq("pi_abc"), anyLong(), anyString()))
                .thenReturn(new RefundResult("re_123", "failed"));

        handler.handle("{\"refundId\":1}");

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        verify(notificationService).notifyAdminRefundFailed(eq(3L), eq(new BigDecimal("84.99")), anyString());
    }

    @Test
    @DisplayName("a permanent Stripe rejection is recorded FAILED and surfaced to an admin, not retried")
    void permanentRejectionDoesNotRetry() throws Exception {
        Refund refund = pending(1L);
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));
        InvalidRequestException rejected = mock(InvalidRequestException.class);
        when(rejected.getMessage()).thenReturn("Charge ch_1 has already been refunded.");
        when(stripeService.issueRefund(eq("pi_abc"), anyLong(), anyString())).thenThrow(rejected);

        handler.handle("{\"refundId\":1}"); // must not throw

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureReason()).contains("already");
        verify(notificationService).notifyAdminRefundFailed(eq(3L), any(), anyString());
    }

    @Test
    @DisplayName("a transient Stripe failure is rethrown so the outbox backs off and retries")
    void transientFailureIsRethrown() throws Exception {
        Refund refund = pending(1L);
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(stripeService.issueRefund(eq("pi_abc"), anyLong(), anyString()))
                .thenThrow(new ApiConnectionException("connection reset"));

        assertThatThrownBy(() -> handler.handle("{\"refundId\":1}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("will retry");

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        verify(notificationService, never()).notifyAdminRefundFailed(any(), any(), anyString());
    }
}
