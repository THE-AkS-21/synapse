package com.skaeht.synapse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * ARCHITECTURE NOTE: Local L1 Cache (Caffeine)
 * While Redis serves as our distributed L2 cache, Caffeine acts as an ultra-fast,
 * in-memory L1 cache for objects that are heavily read but rarely mutated.
 * This significantly reduces network round-trips to Redis or PostgreSQL for hot data.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // High-read, low-write profile: User lookups during JWT validation
        cacheManager.registerCustomCache("users",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(1000)
                        .recordStats() // Exposes hit/miss metrics to Micrometer/Actuator
                        .build());

        // Volatile profile: Chat history buffers
        cacheManager.registerCustomCache("messages",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(5000)
                        .build());

        // Moderate-read profile: Room metadata and participant lists
        cacheManager.registerCustomCache("rooms",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());

        return cacheManager;
    }
}