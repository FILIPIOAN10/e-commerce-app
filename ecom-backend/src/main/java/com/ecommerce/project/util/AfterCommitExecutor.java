package com.ecommerce.project.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a side effect only once the current transaction has committed.
 * <p>
 * Use this for work in an external system that the database transaction cannot
 * roll back — evicting a Redis cache, purging a reservation, calling out. If the
 * work runs before the commit and the commit then fails, the two systems
 * disagree. If there is no active transaction (a direct call, a test) the action
 * runs immediately.
 * <p>
 * A failure in the deferred action is logged, not rethrown: the transaction has
 * already committed and there is nothing left to abort.
 */
@Slf4j
@Component
public class AfterCommitExecutor {

    public void execute(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.warn("Deferred post-commit action failed", e);
                }
            }
        });
    }
}
