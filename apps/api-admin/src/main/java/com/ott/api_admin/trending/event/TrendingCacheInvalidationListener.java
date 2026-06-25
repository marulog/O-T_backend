package com.ott.api_admin.trending.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrendingCacheInvalidationListener {

    private static final String CACHE_NAME = "trending";
    private static final String CACHE_KEY = "all";

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrendingCacheInvalidation(TrendingCacheInvalidationEvent event) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(CACHE_KEY);
            log.info("trending cache evicted (key={})", CACHE_KEY);
        }
    }
}