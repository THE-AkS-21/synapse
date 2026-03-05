package com.skaeht.synapse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for message buffering.
 * Controls batch size, flush intervals, and retry behavior.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "message.buffer")
public class MessageBufferConfig {

    /**
     * Maximum number of messages to batch before forcing a flush
     */
    private int maxBatchSize = 100;

    /**
     * Maximum time (in milliseconds) to wait before flushing buffer
     */
    private long flushIntervalMs = 5000;

    /**
     * Maximum number of retry attempts for failed batch inserts
     */
    private int maxRetries = 3;

    /**
     * Enable/disable message buffering feature
     */
    private boolean enabled = true;

    /**
     * Initial capacity for the message buffer
     */
    private int initialCapacity = 200;

    /**
     * Delay (in milliseconds) before retrying a failed batch insert
     */
    private long retryDelayMs = 1000;
}
