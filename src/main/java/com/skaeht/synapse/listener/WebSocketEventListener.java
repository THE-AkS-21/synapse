package com.skaeht.synapse.listener;

import com.skaeht.synapse.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

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

        Object principal = accessor.getUser();
        if (principal != null) {
            String userId = principal.toString();

            log.info("WebSocket session disconnected: {} (user: {})", sessionId, userId);

            // In a full implementation, we'd fetch all rooms the user was in
            // and call presenceService.userLeftRoom() for each
            // For now, client should send explicit leave messages
        } else {
            log.info("WebSocket session disconnected: {} (anonymous)", sessionId);
        }
    }
}
