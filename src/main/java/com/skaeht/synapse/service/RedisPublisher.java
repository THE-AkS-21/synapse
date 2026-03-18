package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class RedisPublisher {

    private static final String ROOM_CHANNEL_PREFIX = "chat.room.";
    private static final String DIRECT_CHANNEL_PREFIX = "chat.direct.";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Async
    public CompletableFuture<Void> publish(ChatMessage chatMessage) {
        try {
            String channel = getChannelForRoom(chatMessage.getRoomId());
            redisTemplate.convertAndSend(channel, chatMessage);
            log.debug("Published message {} to channel: {}", chatMessage.getId(), channel);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to publish message to Redis: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private String getChannelForRoom(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return ROOM_CHANNEL_PREFIX + "general";
        }
        return ROOM_CHANNEL_PREFIX + roomId;
    }

    public static String getRoomChannelPattern() {
        return ROOM_CHANNEL_PREFIX + "*";
    }
}