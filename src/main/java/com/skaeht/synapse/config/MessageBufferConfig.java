package com.skaeht.synapse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ARCHITECTURE NOTE: Message Buffer Tuning
 * Centralizes the configuration for the MessageBufferService.
 * Exposing these via @ConfigurationProperties allows DevOps to dynamically tune the
 * batch sizes and flush intervals in 'application.yml' without requiring a code recompilation,
 * enabling rapid scaling adjustments during sudden traffic spikes.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "message.buffer")
public class MessageBufferConfig {

    private int maxBatchSize = 100;
    private long flushIntervalMs = 5000;
    private int maxRetries = 3;
    private boolean enabled = true;
    private int initialCapacity = 200;
    private long retryDelayMs = 1000;
}