package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.config.MessageBufferConfig;
import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream-based message buffer service for crash-resilient message
 * persistence.
 * Messages are pushed to a Redis Stream and consumed in batches for database
 * writes.
 * If the server crashes, messages remain in Redis and are processed on restart.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "message.buffer.stream.enabled", havingValue = "true", matchIfMissing = false)
public class RedisStreamMessageBufferService {

    private static final String STREAM_KEY = "messages:pending:stream";
    private static final String CONSUMER_GROUP = "batch-processor";

    private final MessageRepository messageRepository;
    private final MessageBufferConfig config;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String consumerId;

    // Metrics
    private final Counter bufferedMessagesCounter;
    private final Counter flushedMessagesCounter;
    private final Counter failedFlushCounter;
    private final Timer flushTimer;
    private final Counter streamPendingCounter;

    public RedisStreamMessageBufferService(
            MessageRepository messageRepository,
            MessageBufferConfig config,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.config = config;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.consumerId = generateConsumerId();

        // Initialize metrics
        this.bufferedMessagesCounter = Counter.builder("message.buffer.stream.buffered")
                .description("Total number of messages buffered to stream")
                .register(meterRegistry);

        this.flushedMessagesCounter = Counter.builder("message.buffer.stream.flushed")
                .description("Total number of messages flushed from stream")
                .register(meterRegistry);

        this.failedFlushCounter = Counter.builder("message.buffer.stream.flush.failed")
                .description("Number of failed flush operations")
                .register(meterRegistry);

        this.flushTimer = Timer.builder("message.buffer.stream.flush.time")
                .description("Time taken to flush messages from stream")
                .register(meterRegistry);

        this.streamPendingCounter = Counter.builder("message.buffer.stream.pending")
                .description("Pending messages in stream")
                .register(meterRegistry);
    }

    @PostConstruct
    public void initialize() {
        log.info("RedisStreamMessageBufferService initializing with stream key: {}", STREAM_KEY);
        initializeConsumerGroup();
        log.info("RedisStreamMessageBufferService initialized with consumerId: {}", consumerId);
    }

    /**
     * Create consumer group if it doesn't exist
     */
    private void initializeConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
            log.info("Created consumer group: {}", CONSUMER_GROUP);
        } catch (Exception e) {
            // Group likely already exists
            log.info("Consumer group {} already exists or error creating: {}", CONSUMER_GROUP, e.getMessage());
        }
    }

    /**
     * Generate unique consumer ID for this instance
     */
    private String generateConsumerId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return "consumer-" + hostname + "-" + System.currentTimeMillis();
        } catch (Exception e) {
            return "consumer-" + System.currentTimeMillis();
        }
    }

    /**
     * Add a message to the Redis Stream for asynchronous persistence
     */
    public void bufferMessage(ChatMessage chatMessage) {
        try {
            Map<String, String> messageData = new HashMap<>();
            messageData.put("id", chatMessage.id());
            messageData.put("roomId", chatMessage.roomId());
            messageData.put("senderId", String.valueOf(chatMessage.senderId()));
            messageData.put("from", chatMessage.from());
            messageData.put("content", chatMessage.content());
            messageData.put("timestamp", String.valueOf(chatMessage.timestamp()));

            RecordId recordId = redisTemplate.opsForStream().add(STREAM_KEY, messageData);
            bufferedMessagesCounter.increment();

            log.debug("Buffered message {} to stream with recordId {}", chatMessage.id(), recordId);
        } catch (Exception e) {
            log.error("Failed to buffer message {} to stream: {}", chatMessage.id(), e.getMessage(), e);
        }
    }

    /**
     * Scheduled task to flush messages from Redis Stream to database
     */
    @Scheduled(fixedDelayString = "${message.buffer.flush-interval-ms:5000}")
    public void flushBuffer() {
        try {
            // Read messages from stream using consumer group
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(CONSUMER_GROUP, consumerId),
                    StreamReadOptions.empty().count(config.getMaxBatchSize()).block(Duration.ofSeconds(1)),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) {
                return;
            }

            log.info("Read {} messages from stream for processing", records.size());

            flushTimer.record(() -> {
                try {
                    List<ChatMessage> chatMessages = convertRecordsToChatMessages(records);
                    performBatchInsert(chatMessages);

                    // Acknowledge successful processing
                    List<RecordId> recordIds = records.stream()
                            .map(MapRecord::getId)
                            .toList();

                    Long ackCount = redisTemplate.opsForStream().acknowledge(
                            STREAM_KEY,
                            CONSUMER_GROUP,
                            recordIds.toArray(new RecordId[0]));

                    flushedMessagesCounter.increment(records.size());
                    log.info("Successfully flushed and acknowledged {} messages (ack count: {})",
                            records.size(), ackCount);

                } catch (Exception e) {
                    log.error("Failed to flush messages from stream: {}", e.getMessage(), e);
                    failedFlushCounter.increment();
                    // Don't acknowledge - messages will be reprocessed
                }
            });

        } catch (Exception e) {
            log.error("Error reading from stream: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert Redis Stream records to ChatMessage DTOs
     */
    private List<ChatMessage> convertRecordsToChatMessages(List<MapRecord<String, Object, Object>> records) {
        List<ChatMessage> messages = new ArrayList<>();

        for (MapRecord<String, Object, Object> record : records) {
            try {
                Map<Object, Object> value = record.getValue();
                Long senderId = value.get("senderId") != null ? Long.parseLong(value.get("senderId").toString()) : null;
                ChatMessage chatMessage = new ChatMessage(
                        value.get("id").toString(),
                        value.get("roomId").toString(),
                        senderId,
                        value.get("from").toString(),
                        value.get("content").toString(),
                        Long.parseLong(value.get("timestamp").toString()));
                messages.add(chatMessage);
            } catch (Exception e) {
                log.error("Failed to convert record {} to ChatMessage: {}", record.getId(), e.getMessage());
            }
        }

        return messages;
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
                .senderId(chatMessage.senderId())
                .content(chatMessage.content())
                .timestamp(chatMessage.timestamp())
                .build();
    }

    /**
     * Get pending message count from stream (for monitoring)
     */
    public long getPendingCount() {
        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(STREAM_KEY, CONSUMER_GROUP);
            return summary.getTotalPendingMessages();
        } catch (Exception e) {
            log.error("Failed to get pending count: {}", e.getMessage());
            return 0;
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down RedisStreamMessageBufferService, processing remaining messages...");
        // Final flush attempt
        flushBuffer();
    }
}
