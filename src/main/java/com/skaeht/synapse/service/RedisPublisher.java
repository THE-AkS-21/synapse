package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing messages to Redis channels with room-based routing.
 * Supports dynamic channel creation based on roomId for targeted message
 * delivery.
 */
@Service
@Slf4j
public class RedisPublisher {

    private static final String ROOM_CHANNEL_PREFIX = "chat.room.";
    private static final String DIRECT_CHANNEL_PREFIX = "chat.direct.";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Publish message to a room-specific channel asynchronously
     * 
     * @param chatMessage Message to publish
     * @return CompletableFuture that completes when message is published
     */
    @Async
    public CompletableFuture<Void> publish(ChatMessage chatMessage) {
        try {
            String channel = getChannelForRoom(chatMessage.roomId());
            redisTemplate.convertAndSend(channel, chatMessage);
            log.debug("Published message {} to channel: {}", chatMessage.id(), channel);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to publish message to Redis: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get the Redis channel name for a given room ID
     */
    private String getChannelForRoom(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return ROOM_CHANNEL_PREFIX + "general"; // Default room
        }
        return ROOM_CHANNEL_PREFIX + roomId;
    }

    /**
     * Get the channel prefix for pattern subscriptions
     */
    public static String getRoomChannelPattern() {
        return ROOM_CHANNEL_PREFIX + "*";
    }
}