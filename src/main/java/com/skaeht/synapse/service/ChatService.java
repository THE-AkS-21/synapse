package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ChatService {

    private static final int MAX_MESSAGE_LENGTH = 5000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RedisPublisher redisPublisher;
    private final MessageBufferService messageBufferService;

    public ChatService(
            RedisTemplate<String, Object> redisTemplate,
            MessageRepository messageRepository,
            RoomRepository roomRepository,
            UserRepository userRepository,
            RedisPublisher redisPublisher,
            @Autowired(required = false) MessageBufferService messageBufferService) {
        this.redisTemplate = redisTemplate;
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.redisPublisher = redisPublisher;
        this.messageBufferService = messageBufferService;
    }

    /**
     * Core message processing pipeline.
     * Features:
     * 1. Redis-backed rate limiting (max 5 messages / 2 seconds per user).
     * 2. Synchronous publishing to Redis Pub/Sub for immediate WebSocket fanout.
     * 3. Asynchronous persistence via MessageBufferService (if enabled) to reduce database write contention.
     * 4. Optimization: Uses getReferenceById for direct proxy injection without firing a SELECT query.
     */
    @Async
    @Transactional
    public CompletableFuture<ChatMessage> sendMessage(String content, Long senderId, String senderUsername, String roomId) {

        String rateLimitKey = "rate_limit:msg:" + senderId;
        Long msgCount = redisTemplate.opsForValue().increment(rateLimitKey);
        if (msgCount != null && msgCount == 1) redisTemplate.expire(rateLimitKey, Duration.ofSeconds(2));
        if (msgCount != null && msgCount > 5) throw new IllegalStateException("Sending too fast.");
        if (!isValidMessage(content)) throw new IllegalArgumentException("Invalid message");

        long timestamp = System.currentTimeMillis();
        ChatMessage chatMessage = new ChatMessage(roomId, senderId, senderUsername, content, timestamp);

        redisPublisher.publish(chatMessage);

        if (messageBufferService != null) {
            messageBufferService.bufferMessage(chatMessage);
        } else {
            // Utilizes getReferenceById to prevent unnecessary DB SELECT queries
            Message dbMessage = Message.builder()
                    .messageId(chatMessage.getId())
                    .room(roomRepository.getReferenceById(chatMessage.getRoomId()))
                    .sender(userRepository.getReferenceById(senderId))
                    .content(content)
                    .timestamp(timestamp)
                    .build();
            messageRepository.save(dbMessage);
        }

        return CompletableFuture.completedFuture(chatMessage);
    }

    /**
     * Overloaded helper for sending messages to a default "general" room.
     */
    @Async
    @Transactional
    public CompletableFuture<ChatMessage> sendMessage(String content, Long senderId, String senderUsername) {
        return sendMessage(content, senderId, senderUsername, "general");
    }

    /**
     * Validates message payload to prevent empty submissions or excessively large payloads
     * that could impact database performance or cause client-side rendering issues.
     */
    public boolean isValidMessage(String content) {
        return content != null && !content.trim().isEmpty() && content.length() <= MAX_MESSAGE_LENGTH;
    }
}