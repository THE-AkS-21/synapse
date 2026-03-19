package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.PresenceEvent;
import com.skaeht.synapse.dto.event.PresenceType;
import com.skaeht.synapse.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ARCHITECTURE NOTE: Presence System
 * This service implements a two-tier presence architecture:
 * 1. Fast Path (Redis): Handles highly volatile state (online status, typing indicators)
 * with automatic TTL expiries to ensure no ghost sessions remain if a client disconnects ungracefully.
 * 2. Slow Path (PostgreSQL): A write-behind cache mechanism syncs active heartbeats to the persistent
 * database periodically, ensuring we don't hammer the relational DB on every WebSocket tick.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "presence.enabled", havingValue = "true", matchIfMissing = true)
public class PresenceService {

    private static final String ONLINE_KEY_PREFIX = "room:%s:online";
    private static final String TYPING_KEY_PREFIX = "room:%s:typing";
    private static final String HEARTBEAT_KEY_PREFIX = "user:%s:heartbeat";
    private static final String USER_ROOMS_PREFIX = "user:%s:rooms";

    private static final long HEARTBEAT_TTL_SECONDS = 60;
    private static final long TYPING_TTL_SECONDS = 5;

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    // Observability metrics for Grafana/Prometheus dashboards
    private final Counter presenceEventsCounter;
    private final Counter heartbeatMissedCounter;

    public PresenceService(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            UserRepository userRepository,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;

        this.presenceEventsCounter = Counter.builder("presence.events.processed")
                .description("Total number of presence events processed (joins, leaves, typing)")
                .tag("type", "all")
                .register(meterRegistry);

        this.heartbeatMissedCounter = Counter.builder("presence.heartbeat.missed")
                .description("Number of missed heartbeats leading to user removal (ghost sessions)")
                .register(meterRegistry);
    }

    public void userJoinedRoom(String userId, String roomId) {
        redisTemplate.opsForSet().add(String.format(ONLINE_KEY_PREFIX, roomId), userId);
        redisTemplate.opsForSet().add(String.format(USER_ROOMS_PREFIX, userId), roomId);

        updateHeartbeat(userId);
        presenceEventsCounter.increment();

        broadcastPresenceUpdate(roomId, userId, PresenceType.ONLINE);
    }

    public void userLeftRoom(String userId, String roomId) {
        redisTemplate.opsForSet().remove(String.format(ONLINE_KEY_PREFIX, roomId), userId);
        redisTemplate.opsForSet().remove(String.format(TYPING_KEY_PREFIX, roomId), userId);
        redisTemplate.opsForSet().remove(String.format(USER_ROOMS_PREFIX, userId), roomId);

        presenceEventsCounter.increment();
        broadcastPresenceUpdate(roomId, userId, PresenceType.OFFLINE);
    }

    public void userStartedTyping(String userId, String roomId) {
        String typingKey = String.format(TYPING_KEY_PREFIX, roomId);
        redisTemplate.opsForSet().add(typingKey, userId);

        // Rolling TTL: Resets to 5 seconds every time a keystroke is registered
        redisTemplate.expire(typingKey, Duration.ofSeconds(TYPING_TTL_SECONDS));

        presenceEventsCounter.increment();
        broadcastPresenceUpdate(roomId, userId, PresenceType.TYPING);
    }

    public void userStoppedTyping(String userId, String roomId) {
        redisTemplate.opsForSet().remove(String.format(TYPING_KEY_PREFIX, roomId), userId);
        presenceEventsCounter.increment();
        broadcastPresenceUpdate(roomId, userId, PresenceType.STOPPED_TYPING);
    }

    /**
     * Refreshes the user's active session TTL.
     * If the client drops offline (e.g., closes laptop, loses 5G), this key expires naturally,
     * allowing the system to accurately reflect offline status without requiring a formal 'leave' event.
     */
    public void updateHeartbeat(String userId) {
        redisTemplate.opsForValue().set(
                String.format(HEARTBEAT_KEY_PREFIX, userId),
                Instant.now().toString(),
                HEARTBEAT_TTL_SECONDS,
                TimeUnit.SECONDS);
    }

    public Set<String> getOnlineUsers(String roomId) {
        return redisTemplate.opsForSet().members(String.format(ONLINE_KEY_PREFIX, roomId));
    }

    public Set<String> getTypingUsers(String roomId) {
        return redisTemplate.opsForSet().members(String.format(TYPING_KEY_PREFIX, roomId));
    }

    private void broadcastPresenceUpdate(String roomId, String userId, PresenceType type) {
        PresenceEvent event = new PresenceEvent(
                roomId,
                userId,
                type,
                System.currentTimeMillis(),
                getOnlineUsers(roomId),
                getTypingUsers(roomId));

        messagingTemplate.convertAndSend("/topic/presence/" + roomId, event);
    }

    /**
     * Write-Behind Cache implementation.
     * Runs asynchronously every 5 minutes to flush volatile Redis presence data down to the persistent DB.
     * PERFORMANCE NOTE: Uses SCAN instead of KEYS to prevent blocking the single-threaded Redis event loop
     * during high scale scenarios.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void syncPresenceToDatabase() {
        Set<String> keys = new HashSet<>();

        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory().getConnection()
                .scan(ScanOptions.scanOptions().match("user:*:heartbeat").count(100).build())) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        } catch (Exception e) {
            log.error("Failed to execute Redis SCAN for presence sync", e);
            return;
        }

        if (keys.isEmpty()) return;

        log.info("Write-Behind Sync: Updating last_seen in Postgres for {} active users", keys.size());

        for (String key : keys) {
            try {
                // Key format is "user:{email}:heartbeat"
                String userEmail = key.split(":")[1];
                userRepository.findByEmail(userEmail).ifPresent(user -> {
                    user.setLastSeen(Instant.now());
                    userRepository.save(user);
                });
            } catch (Exception e) {
                log.error("Failed to sync presence for key {}", key, e);
            }
        }
    }
}