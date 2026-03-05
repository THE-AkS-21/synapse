package com.skaeht.synapse.service;

import com.skaeht.synapse.config.MessageBufferConfig;
import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background service that buffers chat messages and persists them in batches.
 * Decouples database writes from the real-time message flow to improve
 * performance.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "message.buffer.enabled", havingValue = "true", matchIfMissing = true)
public class MessageBufferService {

    private final MessageRepository messageRepository;
    private final MessageBufferConfig config;
    private final BlockingQueue<ChatMessage> messageBuffer;

    // Metrics
    private final Counter bufferedMessagesCounter;
    private final Counter flushedMessagesCounter;
    private final Counter failedFlushCounter;
    private final Timer flushTimer;
    private final AtomicInteger currentBufferSize;

    public MessageBufferService(
            MessageRepository messageRepository,
            MessageBufferConfig config,
            MeterRegistry meterRegistry) {
        this.messageRepository = messageRepository;
        this.config = config;
        this.messageBuffer = new LinkedBlockingQueue<>(config.getInitialCapacity());

        // Initialize metrics
        this.bufferedMessagesCounter = Counter.builder("message.buffer.buffered")
                .description("Total number of messages buffered")
                .register(meterRegistry);

        this.flushedMessagesCounter = Counter.builder("message.buffer.flushed")
                .description("Total number of messages flushed to database")
                .register(meterRegistry);

        this.failedFlushCounter = Counter.builder("message.buffer.flush.failed")
                .description("Number of failed flush operations")
                .register(meterRegistry);

        this.flushTimer = Timer.builder("message.buffer.flush.time")
                .description("Time taken to flush messages")
                .register(meterRegistry);

        this.currentBufferSize = meterRegistry.gauge("message.buffer.size", new AtomicInteger(0));
    }

    @PostConstruct
    public void initialize() {
        log.info("MessageBufferService initialized with maxBatchSize={}, flushIntervalMs={}",
                config.getMaxBatchSize(), config.getFlushIntervalMs());
    }

    /**
     * Add a message to the buffer for asynchronous persistence
     */
    public void bufferMessage(ChatMessage chatMessage) {
        try {
            boolean added = messageBuffer.offer(chatMessage);
            if (added) {
                bufferedMessagesCounter.increment();
                currentBufferSize.set(messageBuffer.size());
                log.debug("Buffered message {} for room {}", chatMessage.id(), chatMessage.roomId());

                // Trigger immediate flush if buffer is full
                if (messageBuffer.size() >= config.getMaxBatchSize()) {
                    log.info("Buffer reached max size ({}), triggering immediate flush", config.getMaxBatchSize());
                    flushBuffer();
                }
            } else {
                log.warn("Message buffer is full, dropping message {}", chatMessage.id());
            }
        } catch (Exception e) {
            log.error("Failed to buffer message {}: {}", chatMessage.id(), e.getMessage(), e);
        }
    }

    /**
     * Scheduled task to flush buffered messages to database
     * Runs every configured interval
     */
    @Scheduled(fixedDelayString = "${message.buffer.flush-interval-ms:5000}")
    public void flushBuffer() {
        if (messageBuffer.isEmpty()) {
            return;
        }

        List<ChatMessage> messagesToFlush = new ArrayList<>();
        messageBuffer.drainTo(messagesToFlush, config.getMaxBatchSize());

        if (messagesToFlush.isEmpty()) {
            return;
        }

        log.info("Flushing {} messages to database", messagesToFlush.size());

        flushTimer.record(() -> {
            try {
                performBatchInsert(messagesToFlush);
                flushedMessagesCounter.increment(messagesToFlush.size());
                currentBufferSize.set(messageBuffer.size());
                log.info("Successfully flushed {} messages to database", messagesToFlush.size());
            } catch (Exception e) {
                log.error("Failed to flush messages: {}", e.getMessage(), e);
                failedFlushCounter.increment();
                handleFlushFailure(messagesToFlush);
            }
        });
    }

    /**
     * Perform batch insert of messages into database
     */
    private void performBatchInsert(List<ChatMessage> chatMessages) {
        List<Message> dbMessages = chatMessages.stream()
                .map(this::convertToEntity)
                .toList();

        messageRepository.saveAll(dbMessages);
        log.debug("Batch inserted {} messages", dbMessages.size());
    }

    /**
     * Convert ChatMessage DTO to Message entity
     */
    private Message convertToEntity(ChatMessage chatMessage) {
        return Message.builder()
                .messageId(chatMessage.id())
                .roomId(chatMessage.roomId())
                .senderUsername(chatMessage.from())
                .content(chatMessage.content())
                .timestamp(chatMessage.timestamp())
                .build();
    }

    /**
     * Handle failed flush attempts with retry logic
     */
    private void handleFlushFailure(List<ChatMessage> failedMessages) {
        log.warn("Attempting to retry failed flush for {} messages", failedMessages.size());

        int retryCount = 0;
        while (retryCount < config.getMaxRetries()) {
            try {
                Thread.sleep(config.getRetryDelayMs());
                performBatchInsert(failedMessages);
                log.info("Successfully retried flush after {} attempts", retryCount + 1);
                flushedMessagesCounter.increment(failedMessages.size());
                return;
            } catch (Exception e) {
                retryCount++;
                log.error("Retry attempt {} failed: {}", retryCount, e.getMessage());
            }
        }

        log.error("Failed to flush {} messages after {} retries. Messages may be lost!",
                failedMessages.size(), config.getMaxRetries());

        // TODO: Consider dead letter queue or persistent logging for failed messages
    }

    /**
     * Graceful shutdown - flush remaining messages
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MessageBufferService, flushing remaining {} messages", messageBuffer.size());
        flushBuffer();
    }

    /**
     * Get current buffer size (for monitoring)
     */
    public int getBufferSize() {
        return messageBuffer.size();
    }
}
