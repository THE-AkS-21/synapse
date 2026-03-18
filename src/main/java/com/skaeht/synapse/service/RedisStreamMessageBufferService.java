package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.redisson.api.stream.StreamCreateGroupArgs;

@Slf4j
@Service
@Primary
public class RedisStreamMessageBufferService extends MessageBufferService {

    private final RedissonClient redissonClient;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    private final String streamKey;
    private final String consumerGroup;
    private final String consumerId;
    private final int batchSize;

    private final ExecutorService consumerExecutor;
    private volatile boolean isRunning = true;

    public RedisStreamMessageBufferService(
            RedissonClient redissonClient,
            MessageRepository messageRepository,
            RoomRepository roomRepository,
            UserRepository userRepository,
            @Value("${chat.buffer.max-batch-size:100}") int batchSize,
            @Value("${chat.buffer.flush-interval-ms:5000}") long flushIntervalMs,
            @Value("${chat.stream.key:messages:pending:stream}") String streamKey,
            @Value("${chat.stream.group:batch-processor}") String consumerGroup) {

        super(messageRepository, roomRepository, userRepository, batchSize, flushIntervalMs);
        this.redissonClient = redissonClient;
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;

        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        this.batchSize = batchSize;

        String hostname = "unknown";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Could not determine hostname for consumer ID");
        }
        this.consumerId = "consumer-" + hostname + "-" + System.currentTimeMillis();

        this.consumerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RedisStreamConsumer");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void init() {
        log.info("RedisStreamMessageBufferService initializing with stream key: {}", streamKey);
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        try {
            // UPDATED: Redisson 3.27+ API syntax for creating a consumer group
            stream.createGroup(StreamCreateGroupArgs.name(consumerGroup).id(StreamMessageId.ALL));
        } catch (Exception e) {
            log.info("Consumer group {} already exists or error creating: {}", consumerGroup, e.getMessage());
        }
        log.info("RedisStreamMessageBufferService initialized with consumerId: {}", consumerId);
        consumerExecutor.submit(this::consumeLoop);
    }

    @Override
    public void bufferMessage(ChatMessage message) {
        try {
            RStream<String, String> stream = redissonClient.getStream(streamKey);
            Map<String, String> data = new HashMap<>();
            data.put("id", message.getId());
            data.put("roomId", message.getRoomId());
            data.put("senderId", String.valueOf(message.getSenderId()));
            data.put("content", message.getContent());
            data.put("timestamp", String.valueOf(message.getTimestamp()));

            stream.add(StreamAddArgs.entries(data));
        } catch (Exception e) {
            log.error("Failed to add message to Redis Stream, falling back to super.bufferMessage", e);
            super.bufferMessage(message);
        }
    }

    private void consumeLoop() {
        RStream<String, String> stream = redissonClient.getStream(streamKey);

        while (isRunning) {
            try {
                Map<StreamMessageId, Map<String, String>> messages = stream.readGroup(
                        consumerGroup,
                        consumerId,
                        StreamReadGroupArgs.neverDelivered().count(batchSize).timeout(Duration.ofSeconds(2))
                );

                if (messages != null && !messages.isEmpty()) {
                    processBatch(messages, stream);
                }
            } catch (Exception e) {
                if (isRunning) {
                    log.error("Error reading from Redis Stream", e);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private void processBatch(Map<StreamMessageId, Map<String, String>> streamData, RStream<String, String> stream) {
        List<Message> entities = streamData.values().stream()
                .map(this::mapFromStreamData)
                .toList();

        try {
            messageRepository.saveAll(entities);
            StreamMessageId[] ids = streamData.keySet().toArray(new StreamMessageId[0]);
            stream.ack(consumerGroup, ids);
        } catch (Exception e) {
            log.error("Failed to save batch to DB. Messages will remain pending.", e);
        }
    }

    private Message mapFromStreamData(Map<String, String> data) {
        return Message.builder()
                .messageId(data.get("id"))
                .room(roomRepository.getReferenceById(data.get("roomId")))
                .sender(userRepository.getReferenceById(Long.parseLong(data.get("senderId"))))
                .content(data.get("content"))
                .timestamp(Long.parseLong(data.get("timestamp")))
                .isDeleted(false)
                .build();
    }

    @PreDestroy
    @Override
    public void shutdown() {
        log.info("Shutting down RedisStreamMessageBufferService, processing remaining messages...");
        isRunning = false;
        consumerExecutor.shutdown();
        try {
            if (!consumerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                consumerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        super.shutdown();
    }
}