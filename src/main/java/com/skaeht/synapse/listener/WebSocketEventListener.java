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
 * ARCHITECTURE NOTE: The "Dirty Disconnect" Catcher
 * * In a real-time WebSocket application, clients rarely disconnect cleanly. Users close their
 * laptop lids, drive through tunnels, or kill the browser process. Relying solely on explicit
 * STOMP "DISCONNECT" or "/app/presence/leave" frames will result in "ghost" users remaining
 * online forever.
 * * This listener taps directly into the underlying Spring WebSocket lifecycle events. When the
 * physical TCP connection drops (detected via missed heartbeats or socket closure), this
 * component guarantees the system state is cleaned up and other users are notified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "presence.enabled", havingValue = "true", matchIfMissing = false)
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Fired when a WebSocket connection is successfully established.
     * Note: We do not trigger room joins here because a connection is multiplexed.
     * The user must explicitly subscribe to specific STOMP topics to "join" a room.
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("WebSocket TCP session established: {}", accessor.getSessionId());
    }

    /**
     * Fired when the TCP connection is severed.
     * This is our safety net for graceful degradation during ungraceful client exits.
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        // Fast fail for anonymous or unauthenticated socket drops
        if (accessor.getUser() == null || accessor.getUser().getName() == null) {
            log.debug("Anonymous WebSocket session dropped: {}", accessor.getSessionId());
            return;
        }

        String userId = accessor.getUser().getName();
        log.info("WebSocket session disconnected [Executing Dirty Disconnect Recovery]. Session: {}, User: {}",
                accessor.getSessionId(), userId);

        /*
         * STATE RECOVERY:
         * We query Redis to find exactly which rooms this specific user was actively participating in.
         * We then simulate a graceful "leave" for each room to ensure all active clients update
         * their UI (removing the user from the "Online" list and clearing any stuck typing indicators).
         */
        String userRoomsKey = "user:" + userId + ":rooms";
        Set<String> activeRooms = redisTemplate.opsForSet().members(userRoomsKey);

        if (activeRooms != null && !activeRooms.isEmpty()) {
            log.debug("Broadcasting OFFLINE status for user {} across {} rooms", userId, activeRooms.size());
            activeRooms.forEach(roomId -> presenceService.userLeftRoom(userId, roomId));
        }
    }
}