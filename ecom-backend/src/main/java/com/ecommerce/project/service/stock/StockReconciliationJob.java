package com.ecommerce.project.service.stock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link StockReconciliationService#reconcile()} on a fixed delay. Set
 * {@code app.stock.reconciliation-enabled=false} to disable (tests drive the
 * service directly).
 */
@Slf4j
@Component
public class StockReconciliationJob {

    private final StockReconciliationService reconciliationService;
    private final boolean enabled;

    public StockReconciliationJob(StockReconciliationService reconciliationService,
                                  @Value("${app.stock.reconciliation-enabled:true}") boolean enabled) {
        this.reconciliationService = reconciliationService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.stock.reconciliation-interval-ms:3600000}",
            initialDelayString = "${app.stock.reconciliation-initial-delay-ms:300000}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            reconciliationService.reconcile();
        } catch (Exception e) {
            // Never let a bad tick kill the scheduler thread.
            log.error("Stock ledger reconciliation failed", e);
        }
    }
}
