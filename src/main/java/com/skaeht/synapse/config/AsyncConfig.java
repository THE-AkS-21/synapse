package com.skaeht.synapse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous processing.
 * Optimized for handling high concurrent load.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Configure the async executor with optimized settings for high concurrency.
     * 
     * Pool sizing strategy:
     * - Core pool size: Based on available processors
     * - Max pool size: Higher to handle spikes
     * - Queue capacity: Large enough to buffer during peak loads
     */
    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int processors = Runtime.getRuntime().availableProcessors();

        // Core pool size: 2x processors for I/O bound operations
        executor.setCorePoolSize(processors * 2);

        // Max pool size: 4x processors to handle spikes
        executor.setMaxPoolSize(processors * 4);

        // Queue capacity: Buffer for high load scenarios
        executor.setQueueCapacity(1000);

        // Thread naming for easier debugging
        executor.setThreadNamePrefix("async-");

        // Graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Caller runs policy: If queue is full, execute in caller thread
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("Async executor configured with core pool size: {}, max pool size: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize());

        return executor;
    }

    /**
     * Handle uncaught exceptions in async methods.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Uncaught exception in async method '{}': {}",
                    method.getName(), throwable.getMessage(), throwable);
        };
    }
}
