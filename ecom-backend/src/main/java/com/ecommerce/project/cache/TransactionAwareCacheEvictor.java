package com.ecommerce.project.cache;

import com.ecommerce.project.util.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

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
@Component
@RequiredArgsConstructor
public class TransactionAwareCacheEvictor {

    private final CacheManager cacheManager;
    private final AfterCommitExecutor afterCommit;

    public void evictKeyAfterCommit(String cacheName, Object key) {
        afterCommit.execute(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        });
    }

    public void evictAllAfterCommit(String cacheName) {
        afterCommit.execute(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    public void evictAllAfterCommit(List<String> cacheNames) {
        afterCommit.execute(() -> {
            for (String name : cacheNames) {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            }
        });
    }
}
