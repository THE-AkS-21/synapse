package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.PresenceEvent;
import com.skaeht.synapse.dto.PresenceType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service managing user presence (online/offline/typing) using Redis Sets.
 * Supports distributed presence tracking across multiple server instances.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "presence.enabled", havingValue = "true", matchIfMissing = false)
public class PresenceService {

    private static final String ONLINE_KEY_PREFIX = "room:%s:online";
    private static final String TYPING_KEY_PREFIX = "room:%s:typing";
    private static final String HEARTBEAT_KEY_PREFIX = "user:%s:heartbeat";
    private static final String USER_ROOMS_PREFIX = "user:%s:rooms";

    private static final long HEARTBEAT_TTL_SECONDS = 60;
    private static final long TYPING_TTL_SECONDS = 5;

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final Counter presenceEventsCounter;
    private final Counter heartbeatMissedCounter;

    public PresenceService(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;

        this.presenceEventsCounter = Counter.builder("presence.events.processed")
                .description("Total number of presence events processed")
                .tag("type", "all")
                .register(meterRegistry);

        this.heartbeatMissedCounter = Counter.builder("presence.heartbeat.missed")
                .description("Number of missed heartbeats leading to user removal")
                .register(meterRegistry);
    }

    /**
     * User joined a room - add to online set and broadcast
     */
    public void userJoinedRoom(String userId, String roomId) {
        String onlineKey = String.format(ONLINE_KEY_PREFIX, roomId);
        String userRoomsKey = String.format(USER_ROOMS_PREFIX, userId);

        redisTemplate.opsForSet().add(onlineKey, userId);
        redisTemplate.opsForSet().add(userRoomsKey, roomId);
        updateHeartbeat(userId);

        presenceEventsCounter.increment();
        log.info("User {} joined room {}", userId, roomId);

        broadcastPresenceUpdate(roomId, userId, PresenceType.ONLINE);
    }

    /**
     * User left a room - remove from online and typing sets, broadcast
     */
    public void userLeftRoom(String userId, String roomId) {
        String onlineKey = String.format(ONLINE_KEY_PREFIX, roomId);
        String typingKey = String.format(TYPING_KEY_PREFIX, roomId);
        String userRoomsKey = String.format(USER_ROOMS_PREFIX, userId);

        redisTemplate.opsForSet().remove(onlineKey, userId);
        redisTemplate.opsForSet().remove(typingKey, userId);
        redisTemplate.opsForSet().remove(userRoomsKey, roomId);

        presenceEventsCounter.increment();
        log.info("User {} left room {}", userId, roomId);

        broadcastPresenceUpdate(roomId, userId, PresenceType.OFFLINE);
    }

    /**
     * User started typing in a room
     */
    public void userStartedTyping(String userId, String roomId) {
        String typingKey = String.format(TYPING_KEY_PREFIX, roomId);

        redisTemplate.opsForSet().add(typingKey, userId);
        redisTemplate.expire(typingKey, Duration.ofSeconds(TYPING_TTL_SECONDS));

        presenceEventsCounter.increment();
        log.debug("User {} started typing in room {}", userId, roomId);

        broadcastPresenceUpdate(roomId, userId, PresenceType.TYPING);
    }

    /**
     * User stopped typing in a room
     */
    public void userStoppedTyping(String userId, String roomId) {
        String typingKey = String.format(TYPING_KEY_PREFIX, roomId);

        redisTemplate.opsForSet().remove(typingKey, userId);

        presenceEventsCounter.increment();
        log.debug("User {} stopped typing in room {}", userId, roomId);

        broadcastPresenceUpdate(roomId, userId, PresenceType.STOPPED_TYPING);
    }

    /**
     * Update user heartbeat with TTL for auto-cleanup
     */
    public void updateHeartbeat(String userId) {
        String heartbeatKey = String.format(HEARTBEAT_KEY_PREFIX, userId);

        redisTemplate.opsForValue().set(
                heartbeatKey,
                String.valueOf(System.currentTimeMillis()),
                HEARTBEAT_TTL_SECONDS,
                TimeUnit.SECONDS);

        log.debug("Updated heartbeat for user {}", userId);
    }

    /**
     * Get online users in a room
     */
    public Set<String> getOnlineUsers(String roomId) {
        String onlineKey = String.format(ONLINE_KEY_PREFIX, roomId);
        return redisTemplate.opsForSet().members(onlineKey);
    }

    /**
     * Get typing users in a room
     */
    public Set<String> getTypingUsers(String roomId) {
        String typingKey = String.format(TYPING_KEY_PREFIX, roomId);
        return redisTemplate.opsForSet().members(typingKey);
    }

    /**
     * Broadcast presence update to all room participants
     */
    private void broadcastPresenceUpdate(String roomId, String userId, PresenceType type) {
        Set<String> onlineUsers = getOnlineUsers(roomId);
        Set<String> typingUsers = getTypingUsers(roomId);

        PresenceEvent event = new PresenceEvent(
                roomId,
                userId,
                type,
                System.currentTimeMillis(),
                onlineUsers,
                typingUsers);

        String destination = "/topic/presence/" + roomId;
        messagingTemplate.convertAndSend(destination, event);

        log.debug("Broadcasted {} event for user {} in room {} to {}",
                type, userId, roomId, destination);
    }

    /**
     * Scheduled cleanup of stale users (missed heartbeats)
     * Runs every 30 seconds
     */
    @Scheduled(fixedRateString = "${presence.cleanup.interval.ms:30000}")
    public void cleanupStaleUsers() {
        log.debug("Running stale user cleanup");

        // This is a simplified version - in production, you'd scan all user heartbeat
        // keys
        // For now, we rely on TTL expiration and handle it in the WebSocket disconnect
        // event
    }
}
