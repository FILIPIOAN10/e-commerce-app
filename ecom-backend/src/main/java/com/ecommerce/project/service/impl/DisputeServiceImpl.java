package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Dispute;
import com.ecommerce.project.model.DisputeEvidenceFile;
import com.ecommerce.project.model.DisputeStatus;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.payload.DisputeDTO;
import com.ecommerce.project.payload.DisputeEvidenceFileDTO;
import com.ecommerce.project.repository.DisputeEvidenceFileRepository;
import com.ecommerce.project.repository.DisputeRepository;
import com.ecommerce.project.repository.PaymentRepository;
import com.ecommerce.project.service.DisputeService;
import com.ecommerce.project.service.outbox.OutboxEventPublisher;
import com.ecommerce.project.service.outbox.OutboxEventTypes;
import com.ecommerce.project.service.outbox.payload.DisputeOutboxPayload;
import com.stripe.model.Dispute.EvidenceDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Mirrors Stripe's chargeback lifecycle into the {@code disputes} table.
 *
 * <p>Every entry point is idempotent, because the webhook that calls it is
 * at-least-once and the events can arrive out of order:
 * {@link #openFromStripe} on a dispute that already exists becomes an update;
 * {@link #syncFromStripe} / {@link #closeFromStripe} on one that does not yet
 * exist opens it first. The status change itself is machine-checked by
 * {@link DisputeStatus} — an illegal jump from a stale event is logged and
 * dropped, never applied, so a closed dispute cannot reopen.
 *
 * <p>The two outward effects ({@code DISPUTE_OPENED}, {@code DISPUTE_CLOSED}) are
 * published to the outbox in the same transaction as the row write, so the admin
 * alert survives a crash between commit and notification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceFileRepository evidenceFileRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    @Transactional
    public void openFromStripe(com.stripe.model.Dispute sd) {
        disputeRepository.findByStripeDisputeId(sd.getId()).ifPresentOrElse(
                existing -> applyUpdate(existing, sd),
                () -> {
                    Dispute dispute = Dispute.openedFrom(
                            sd.getId(),
                            sd.getPaymentIntent(),
                            sd.getCharge(),
                            resolveOrderId(sd.getPaymentIntent()),
                            minorToDecimal(sd.getAmount()),
                            sd.getCurrency(),
                            sd.getReason(),
                            sd.getStatus(),
                            dueBy(sd));
                    Dispute saved = disputeRepository.save(dispute);
                    outboxEventPublisher.publish(OutboxEventTypes.DISPUTE_OPENED,
                            new DisputeOutboxPayload(saved.getId()));
                    log.info("Dispute {} opened on order {} — {} {}, reason {}, respond by {}",
                            saved.getStripeDisputeId(), saved.getOrderId(), saved.getAmount(),
                            saved.getCurrency(), saved.getReason(), saved.getEvidenceDueBy());
                });
    }

    @Override
    @Transactional
    public void syncFromStripe(com.stripe.model.Dispute sd) {
        disputeRepository.findByStripeDisputeId(sd.getId())
                .ifPresentOrElse(existing -> applyUpdate(existing, sd), () -> openFromStripe(sd));
    }

    @Override
    @Transactional
    public void closeFromStripe(com.stripe.model.Dispute sd) {
        syncFromStripe(sd); // closed is just an update whose target status is terminal
    }

    private void applyUpdate(Dispute dispute, com.stripe.model.Dispute sd) {
        DisputeStatus before = dispute.getStatus();
        DisputeStatus target = DisputeStatus.fromStripe(sd.getStatus());
        boolean wasTerminal = dispute.isTerminal();

        boolean changed = dispute.transitionTo(target, sd.getStatus());
        if (!changed && target != before) {
            log.warn("Ignored illegal dispute transition {} -> {} for {} (stale or out-of-order event)",
                    before, target, dispute.getStripeDisputeId());
        }

        EvidenceDetails details = sd.getEvidenceDetails();
        if (details != null) {
            if (details.getSubmissionCount() != null && details.getSubmissionCount() > 0) {
                dispute.markEvidenceSubmitted(LocalDateTime.now());
            }
            if (details.getDueBy() != null) {
                dispute.setEvidenceDueBy(epochToLocal(details.getDueBy()));
            }
        }
        if (changed && dispute.isTerminal()) {
            dispute.setOutcomeNote(outcomeNote(target, sd.getStatus()));
        }
        disputeRepository.save(dispute);

        if (changed && dispute.isTerminal() && !wasTerminal) {
            outboxEventPublisher.publish(OutboxEventTypes.DISPUTE_CLOSED,
                    new DisputeOutboxPayload(dispute.getId()));
            log.info("Dispute {} closed: {} ({})", dispute.getStripeDisputeId(), target, sd.getStatus());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeDTO> listAll() {
        return disputeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(d -> toDto(d, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeDTO get(Long disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", "id", disputeId));
        List<DisputeEvidenceFileDTO> files = evidenceFileRepository
                .findByDisputeIdOrderByUploadedAtAsc(disputeId).stream()
                .map(DisputeServiceImpl::toFileDto)
                .toList();
        return toDto(dispute, files);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Long resolveOrderId(String paymentIntentId) {
        if (paymentIntentId == null) {
            return null;
        }
        return paymentRepository.findByPgPaymentId(paymentIntentId)
                .map(p -> {
                    Order order = p.getOrder();
                    return order != null ? order.getId() : null;
                })
                .orElse(null);
    }

    private static BigDecimal minorToDecimal(Long minorUnits) {
        return minorUnits == null ? BigDecimal.ZERO : new BigDecimal(minorUnits).movePointLeft(2);
    }

    private static LocalDateTime dueBy(com.stripe.model.Dispute sd) {
        EvidenceDetails details = sd.getEvidenceDetails();
        return details != null && details.getDueBy() != null ? epochToLocal(details.getDueBy()) : null;
    }

    private static LocalDateTime epochToLocal(long epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    private static String outcomeNote(DisputeStatus status, String rawStripeStatus) {
        return switch (status) {
            case WON -> "Resolved in our favour (" + rawStripeStatus + ").";
            case LOST -> "Resolved against us; funds withdrawn (" + rawStripeStatus + ").";
            default -> "Closed without a win/loss outcome (" + rawStripeStatus + ").";
        };
    }

    private static DisputeDTO toDto(Dispute d, List<DisputeEvidenceFileDTO> files) {
        return new DisputeDTO(
                d.getId(), d.getStripeDisputeId(), d.getPaymentIntentId(), d.getOrderId(),
                d.getAmount(), d.getCurrency(), d.getReason(), d.getStatus().name(), d.getStripeStatus(),
                d.isTerminal(), d.getEvidenceDueBy(), d.getEvidenceSubmittedAt(), d.getOutcomeNote(),
                d.getCreatedAt(), d.getUpdatedAt(), files);
    }

    private static DisputeEvidenceFileDTO toFileDto(DisputeEvidenceFile f) {
        return new DisputeEvidenceFileDTO(f.getId(), f.getOriginalName(), f.getContentType(),
                f.getSizeBytes(), f.getUploadedBy(), f.getUploadedAt());
    }
}
