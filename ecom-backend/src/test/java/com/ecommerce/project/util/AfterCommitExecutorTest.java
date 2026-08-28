package com.ecommerce.project.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AfterCommitExecutor")
class AfterCommitExecutorTest {

    private final AfterCommitExecutor executor = new AfterCommitExecutor();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("runs immediately when there is no transaction")
    void immediateWithoutTransaction() {
        AtomicInteger runs = new AtomicInteger();
        executor.execute(runs::incrementAndGet);
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("defers to afterCommit while a transaction is active")
    void deferredWithinTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        executor.execute(runs::incrementAndGet);
        assertThat(runs).hasValue(0);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("does not run on rollback")
    void notRunOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        executor.execute(runs::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(runs).hasValue(0);
    }

    @Test
    @DisplayName("a failure in the deferred action is swallowed, not propagated")
    void swallowsDeferredFailure() {
        TransactionSynchronizationManager.initSynchronization();

        executor.execute(() -> {
            throw new IllegalStateException("boom");
        });

        // afterCommit must not throw — the transaction has already committed.
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }
}
