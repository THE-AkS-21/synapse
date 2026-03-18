package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.PresenceEvent;
import com.skaeht.synapse.dto.event.PresenceType;
import com.skaeht.synapse.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
                .description("Total number of presence events processed")
                .tag("type", "all")
                .register(meterRegistry);

        this.heartbeatMissedCounter = Counter.builder("presence.heartbeat.missed")
                .description("Number of missed heartbeats leading to user removal")
                .register(meterRegistry);
    }

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

    public void userStartedTyping(String userId, String roomId) {
        String typingKey = String.format(TYPING_KEY_PREFIX, roomId);

        redisTemplate.opsForSet().add(typingKey, userId);
        redisTemplate.expire(typingKey, Duration.ofSeconds(TYPING_TTL_SECONDS));

        presenceEventsCounter.increment();
        log.debug("User {} started typing in room {}", userId, roomId);

        broadcastPresenceUpdate(roomId, userId, PresenceType.TYPING);
    }

    public void userStoppedTyping(String userId, String roomId) {
        String typingKey = String.format(TYPING_KEY_PREFIX, roomId);

        redisTemplate.opsForSet().remove(typingKey, userId);

        presenceEventsCounter.increment();
        log.debug("User {} stopped typing in room {}", userId, roomId);

        broadcastPresenceUpdate(roomId, userId, PresenceType.STOPPED_TYPING);
    }

    // 1. FAST PATH: Memory-Bound Heartbeat
    public void updateHeartbeat(String userId) {
        String heartbeatKey = String.format(HEARTBEAT_KEY_PREFIX, userId);

        redisTemplate.opsForValue().set(
                heartbeatKey,
                Instant.now().toString(),
                HEARTBEAT_TTL_SECONDS,
                TimeUnit.SECONDS);

        log.debug("Updated heartbeat for user {}", userId);
    }

    public Set<String> getOnlineUsers(String roomId) {
        String onlineKey = String.format(ONLINE_KEY_PREFIX, roomId);
        return redisTemplate.opsForSet().members(onlineKey);
    }

    public Set<String> getTypingUsers(String roomId) {
        String typingKey = String.format(TYPING_KEY_PREFIX, roomId);
        return redisTemplate.opsForSet().members(typingKey);
    }

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

    // 2. WRITE-BEHIND CACHE: Background Sync to Postgres
    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    @Transactional
    public void syncPresenceToDatabase() {
        // Scanning for all active heartbeats
        Set<String> keys = redisTemplate.keys("user:*:heartbeat");
        if (keys == null || keys.isEmpty()) return;

        log.info("Write-Behind Sync: Updating last_seen in Postgres for {} active users", keys.size());

        for (String key : keys) {
            try {
                // Extract userId (which is actually the email) from "user:{userId}:heartbeat"
                String userEmail = key.split(":")[1];

                // FIX: Use findByEmail instead of findById, since the keys are storing email addresses
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