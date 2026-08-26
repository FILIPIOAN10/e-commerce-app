package com.ecommerce.project.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Evicts Redis caches only after the current transaction has successfully
 * committed. Without this, {@code @CacheEvict} annotations on
 * {@code @Transactional} methods fire after the method returns but before the
 * commit — so a commit failure (constraint violation, deadlock) leaves Redis
 * evicted while Postgres rolls back, serving stale or missing data until the
 * next cache rebuild.
 * <p>
 * When called outside an active transaction, eviction is immediate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionAwareCacheEvictor {

    private final CacheManager cacheManager;

    public void evictKeyAfterCommit(String cacheName, Object key) {
        runAfterCommit(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        });
    }

    public void evictAllAfterCommit(String cacheName) {
        runAfterCommit(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    public void evictAllAfterCommit(List<String> cacheNames) {
        runAfterCommit(() -> {
            for (String name : cacheNames) {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.warn("Post-commit cache eviction failed", e);
                    }
                }
            });
        } else {
            action.run();
        }
    }
}
