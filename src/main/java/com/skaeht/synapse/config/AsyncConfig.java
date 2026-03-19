package com.skaeht.synapse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous processing and background scheduling.
 * * ARCHITECTURE NOTE FOR SYNAPSE:
 * Real-time chat apps are highly I/O bound (Redis Pub/Sub, PostgreSQL writes, WebSockets).
 * This configuration offloads non-critical path operations (e.g., updating user presence,
 * async database flushing) from the main request threads to prevent blocking the Event Loop.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Defines the primary thread pool for @Async methods.
     * We dynamically size the pool based on the available hardware.
     */
    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Dynamically fetch available CPU cores
        int processors = Runtime.getRuntime().availableProcessors();

        // Core threads: The baseline number of threads kept alive (cores * 2 for I/O bound tasks)
        executor.setCorePoolSize(processors * 2);

        // Max threads: Upper limit during traffic spikes
        executor.setMaxPoolSize(processors * 4);

        // Queue Capacity: How many tasks can wait before we start spinning up new threads towards MaxPoolSize.
        executor.setQueueCapacity(1000);

        // Naming prefix for easier debugging in logs and thread dumps
        executor.setThreadNamePrefix("synapse-async-");

        // Graceful Shutdown: Ensure background tasks (like DB writes) finish before killing the app
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Backpressure Strategy (CRITICAL):
        // If the queue (1000) is full, and max threads are busy, CallerRunsPolicy forces the
        // thread that triggered the @Async method to execute it. This acts as a natural throttle
        // and prevents OutOfMemory (OOM) errors or dropped messages.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("Synapse Async Executor initialized | Cores: {} | Core Pool: {} | Max Pool: {}",
                processors, executor.getCorePoolSize(), executor.getMaxPoolSize());

        return executor;
    }

    /**
     * Handles exceptions thrown by @Async methods that return void.
     * Without this, exceptions in background threads get swallowed silently and are incredibly hard to debug.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("CRITICAL: Uncaught exception in async background task '{}'. Reason: {}",
                    method.getName(), throwable.getMessage(), throwable);
        };
    }
}