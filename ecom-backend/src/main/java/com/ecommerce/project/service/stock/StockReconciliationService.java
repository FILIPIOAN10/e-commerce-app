package com.ecommerce.project.service.stock;

import com.ecommerce.project.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Checks that the ledger still adds up to the stock figures.
 *
 * <p>The ledger is only as good as the discipline of routing every write through
 * {@link StockLedgerService}, and that discipline is exactly the thing a future
 * change can break without noticing — one {@code setQuantity} somewhere new and
 * the audit trail quietly stops being true. This is the net under that: it
 * cannot repair a discrepancy (it has no way to know what the missing movement
 * was), but it makes one visible instead of silent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockReconciliationService {

    /** One product whose ledger and stock figure disagree. */
    public record Discrepancy(Long productId, int quantity, int ledgerTotal) {
        public int drift() {
            return quantity - ledgerTotal;
        }
    }

    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public List<Discrepancy> findDiscrepancies() {
        return stockMovementRepository.findLedgerDiscrepancies().stream()
                .map(row -> new Discrepancy(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).intValue()))
                .toList();
    }

    /** @return how many products are out of step */
    public int reconcile() {
        List<Discrepancy> discrepancies = findDiscrepancies();
        if (discrepancies.isEmpty()) {
            log.debug("Stock ledger reconciliation: all products agree with their movements");
            return 0;
        }
        log.warn("Stock ledger reconciliation found {} product(s) whose stock does not match "
                 + "their movements — a write bypassed StockLedgerService", discrepancies.size());
        for (Discrepancy discrepancy : discrepancies) {
            log.warn("  product {}: quantity={} but ledger sums to {} (drift {})",
                    discrepancy.productId(), discrepancy.quantity(),
                    discrepancy.ledgerTotal(), discrepancy.drift());
        }
        return discrepancies.size();
    }
}
