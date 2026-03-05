package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * Service for handling chat message business logic with room-based routing.
 * Supports async processing for high-throughput messaging with optional
 * write-behind caching.
 */
@Service
@Slf4j
public class ChatService {

    private static final int MAX_MESSAGE_LENGTH = 5000;

    private final MessageRepository messageRepository;
    private final RedisPublisher redisPublisher;
    private final RoomService roomService;
    private final MessageBufferService messageBufferService;
    private final RedisStreamMessageBufferService redisStreamBufferService;

    public ChatService(
            MessageRepository messageRepository,
            RedisPublisher redisPublisher,
            RoomService roomService,
            @Autowired(required = false) MessageBufferService messageBufferService,
            @Autowired(required = false) RedisStreamMessageBufferService redisStreamBufferService) {
        this.messageRepository = messageRepository;
        this.redisPublisher = redisPublisher;
        this.roomService = roomService;
        this.messageBufferService = messageBufferService;
        this.redisStreamBufferService = redisStreamBufferService;
    }

    /**
     * Send a chat message to a specific room.
     * If MessageBufferService is available, uses write-behind caching for async
     * persistence.
     * Otherwise falls back to synchronous database writes.
     * 
     * @param content        Message content
     * @param senderUsername Username of sender
     * @param roomId         Target room ID
     * @return CompletableFuture with the sent message
     */
    @Async
    @Transactional
    public CompletableFuture<ChatMessage> sendMessage(String content, String senderUsername, String roomId) {
        // Validate message
        if (!isValidMessage(content)) {
            throw new IllegalArgumentException("Invalid message: empty or too long");
        }

        long timestamp = System.currentTimeMillis();

        // Create ChatMessage DTO with auto-generated ID and traceId
        ChatMessage chatMessage = new ChatMessage(roomId, senderUsername, content, timestamp);

        // Publish to Redis immediately for real-time delivery
        redisPublisher.publish(chatMessage);
        log.info("Published message {} to room {} from user {}", chatMessage.id(), roomId, senderUsername);

        // Use buffer service if available, otherwise fall back to synchronous save
        if (messageBufferService != null) {
            // Asynchronous persistence via buffer (FAST PATH)
            messageBufferService.bufferMessage(chatMessage);
            log.debug("Message {} buffered for async persistence", chatMessage.id());
        } else {
            // Synchronous persistence (FALLBACK)
            Message dbMessage = Message.builder()
                    .messageId(chatMessage.id())
                    .roomId(chatMessage.roomId())
                    .senderUsername(senderUsername)
                    .content(content)
                    .timestamp(timestamp)
                    .build();

            messageRepository.save(dbMessage);
            log.debug("Message {} saved synchronously to database", chatMessage.id());
        }

        return CompletableFuture.completedFuture(chatMessage);
    }

    /**
     * Send a message to the default "general" room
     */
    @Async
    @Transactional
    public CompletableFuture<ChatMessage> sendMessage(String content, String senderUsername) {
        return sendMessage(content, senderUsername, "general");
    }

    /**
     * Validate message content
     */
    public boolean isValidMessage(String content) {
        return content != null &&
                !content.trim().isEmpty() &&
                content.length() <= MAX_MESSAGE_LENGTH;
    }
}
