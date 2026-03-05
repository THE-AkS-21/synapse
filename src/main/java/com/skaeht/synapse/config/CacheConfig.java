package com.skaeht.synapse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for caching with Caffeine.
 * Improves performance by caching frequently accessed data.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure cache manager with specific cache configurations for different data
     * types.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // User cache: 1000 users, expire after 1 hour of inactivity
        cacheManager.registerCustomCache("users",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(1000)
                        .recordStats()
                        .build());

        // Message cache: 5000 recent messages, expire after 10 minutes
        cacheManager.registerCustomCache("messages",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(5000)
                        .recordStats()
                        .build());

        // Room cache: 500 rooms, expire after 30 minutes
        cacheManager.registerCustomCache("rooms",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .recordStats()
                        .build());

        return cacheManager;
    }
}
