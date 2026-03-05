package com.skaeht.synapse.listener;

import com.skaeht.synapse.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;

/**
 * Listener for WebSocket connection/disconnection events.
 * Automatically manages user presence when they connect or disconnect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "presence.enabled", havingValue = "true", matchIfMissing = false)
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Handle WebSocket connection event
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        log.info("WebSocket session connected: {}", sessionId);

        // Note: Actual room joining happens through explicit /presence/join messages
        // This just logs the connection
    }

    /**
     * Handle WebSocket disconnection event
     * Automatically remove user from all rooms they were in
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (accessor.getUser() != null) {
            String userId = accessor.getUser().getName();

            log.info("WebSocket session disconnected: {} (user: {})", sessionId, userId);

            // Fetch all rooms this user is active in from Redis
            Set<String> activeRooms = redisTemplate.opsForSet().members("user:" + userId + ":rooms");
            if (activeRooms != null) {
                for (String roomId : activeRooms) {
                    presenceService.userLeftRoom(userId, roomId); // Broadcasts OFFLINE to the room
                }
            }
        } else {
            log.info("WebSocket session disconnected: {} (anonymous)", sessionId);
        }
    }
}
