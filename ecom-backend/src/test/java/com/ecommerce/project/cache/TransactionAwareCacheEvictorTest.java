package com.ecommerce.project.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransactionAwareCacheEvictor tests")
class TransactionAwareCacheEvictorTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private TransactionAwareCacheEvictor evictor;

    private static final List<String> CACHE_NAMES = List.of("publicProducts", "adminProducts");

    @BeforeEach
    void setUp() {
        evictor = new TransactionAwareCacheEvictor(cacheManager);
        when(cacheManager.getCache("publicProducts")).thenReturn(cache);
        when(cacheManager.getCache("adminProducts")).thenReturn(cache);
    }

    @Test
    @DisplayName("evicts immediately when no transaction is active")
    void evictAll_noTransaction_evictsImmediately() {
        evictor.evictAllAfterCommit(CACHE_NAMES);

        verify(cache, times(2)).clear();
    }

    @Test
    @DisplayName("evicts key immediately when no transaction is active")
    void evictKey_noTransaction_evictsImmediately() {
        evictor.evictKeyAfterCommit("publicProducts", 42L);

        verify(cache).evict(42L);
    }

    @Test
    @DisplayName("defers eviction to afterCommit when transaction is active")
    void evictAll_inTransaction_defersToAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            evictor.evictAllAfterCommit(CACHE_NAMES);

            verify(cache, never()).clear();

            List<TransactionSynchronization> syncs =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, syncs.size());

            syncs.get(0).afterCommit();

            verify(cache, times(2)).clear();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("does not evict if transaction rolls back")
    void evictAll_inTransaction_rollback_noEviction() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            evictor.evictAllAfterCommit(CACHE_NAMES);

            List<TransactionSynchronization> syncs =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, syncs.size());

            // Simulate rollback: afterCompletion is called without afterCommit
            syncs.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(cache, never()).clear();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("evicts key after commit when transaction is active")
    void evictKey_inTransaction_defersToAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            evictor.evictKeyAfterCommit("publicProducts", 99L);

            verify(cache, never()).evict(99L);

            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();

            verify(cache).evict(99L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
