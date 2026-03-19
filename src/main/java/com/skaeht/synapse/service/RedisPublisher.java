package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * ARCHITECTURE NOTE: The Fan-Out Publisher
 * In a multi-node backend, WebSocket connections are stateful and pinned to a specific server.
 * If Alice (Node 1) messages Bob (Node 2), Node 1 cannot send it directly.
 * This publisher pushes messages to Redis Pub/Sub, creating a unified distributed event bus
 * so all active nodes receive the message and can push it down their local WebSocket pipes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisPublisher {

    private static final String ROOM_CHANNEL_PREFIX = "chat.room.";
    private final RedisTemplate<String, Object> redisTemplate;

    @Async
    public CompletableFuture<Void> publish(ChatMessage chatMessage) {
        try {
            String channel = getChannelForRoom(chatMessage.getRoomId());
            redisTemplate.convertAndSend(channel, chatMessage);

            log.debug("Published message {} to distributed channel: {}", chatMessage.getId(), channel);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("CRITICAL: Redis Pub/Sub failure. Message {} dropped.", chatMessage.getId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private String getChannelForRoom(String roomId) {
        return ROOM_CHANNEL_PREFIX + (roomId == null || roomId.isEmpty() ? "general" : roomId);
    }
}