package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
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
    private final RedisPublisher redisPublisher;
    private final RoomService roomService;
    private final MessageBufferService messageBufferService;
    private final RedisStreamMessageBufferService redisStreamBufferService;

    public ChatService(
            RedisTemplate<String, Object> redisTemplate,
            MessageRepository messageRepository,
            RedisPublisher redisPublisher,
            RoomService roomService,
            @Autowired(required = false) MessageBufferService messageBufferService,
            @Autowired(required = false) RedisStreamMessageBufferService redisStreamBufferService) {
        this.redisTemplate = redisTemplate;
        this.messageRepository = messageRepository;
        this.redisPublisher = redisPublisher;
        this.roomService = roomService;
        this.messageBufferService = messageBufferService;
        this.redisStreamBufferService = redisStreamBufferService;
    }

    @Async
    @Transactional
    public CompletableFuture<ChatMessage> sendMessage(String content, Long senderId, String senderUsername, String roomId) {

        // Rate limit messages (e.g., max 5 messages per 2 seconds)
        String rateLimitKey = "rate_limit:msg:" + senderId;
        Long msgCount = redisTemplate.opsForValue().increment(rateLimitKey);
        if (msgCount != null && msgCount == 1) {
            redisTemplate.expire(rateLimitKey, Duration.ofSeconds(2));
        }
        if (msgCount != null && msgCount > 5) {
            log.warn("User {} is sending messages too fast", senderUsername);
            throw new IllegalStateException("You are sending messages too fast. Please slow down.");
        }

        // Validate message
        if (!isValidMessage(content)) {
            throw new IllegalArgumentException("Invalid message: empty or too long");
        }

        long timestamp = System.currentTimeMillis();

        ChatMessage chatMessage = new ChatMessage(roomId, senderId, senderUsername, content, timestamp);

        redisPublisher.publish(chatMessage);
        log.info("Published message {} to room {} from user {}", chatMessage.id(), roomId, senderUsername);

        if (messageBufferService != null) {
            messageBufferService.bufferMessage(chatMessage);
            log.debug("Message {} buffered for async persistence", chatMessage.id());
        } else {
            Message dbMessage = Message.builder()
                    .messageId(chatMessage.id())
                    .roomId(chatMessage.roomId())
                    .senderId(senderId)
                    .content(content)
                    .timestamp(timestamp)
                    .build();

            messageRepository.save(dbMessage);
            log.debug("Message {} saved synchronously to database", chatMessage.id());
        }

        return CompletableFuture.completedFuture(chatMessage);
    }

    @Async
    @Transactional
    public CompletableFuture<ChatMessage> sendMessage(String content, Long senderId, String senderUsername) {
        return sendMessage(content, senderId, senderUsername, "general");
    }

    public boolean isValidMessage(String content) {
        return content != null &&
                !content.trim().isEmpty() &&
                content.length() <= MAX_MESSAGE_LENGTH;
    }
}