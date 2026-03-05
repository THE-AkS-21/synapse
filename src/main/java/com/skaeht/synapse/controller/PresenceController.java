package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.TypingEvent;
import com.skaeht.synapse.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket controller for handling presence events (join, leave, typing,
 * heartbeat).
 * Users send presence updates through WebSocket messages.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@ConditionalOnProperty(name = "presence.enabled", havingValue = "true", matchIfMissing = false)
public class PresenceController {

    private final PresenceService presenceService;

    /**
     * User joins a room
     * Client sends: STOMP SEND to /app/presence/join/{roomId}
     */
    @MessageMapping("/presence/join/{roomId}")
    public void joinRoom(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) {
            log.warn("Unauthenticated user attempted to join room {}", roomId);
            return;
        }

        String userId = principal.getName();
        presenceService.userJoinedRoom(userId, roomId);
    }

    /**
     * User leaves a room
     * Client sends: STOMP SEND to /app/presence/leave/{roomId}
     */
    @MessageMapping("/presence/leave/{roomId}")
    public void leaveRoom(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) {
            return;
        }

        String userId = principal.getName();
        presenceService.userLeftRoom(userId, roomId);
    }

    /**
     * User typing indicator
     * Client sends: STOMP SEND to /app/presence/typing/{roomId}
     * Payload: {"isTyping": true/false}
     */
    @MessageMapping("/presence/typing/{roomId}")
    public void typing(
            @DestinationVariable String roomId,
            @Payload TypingEvent event,
            Principal principal) {
        if (principal == null) {
            return;
        }

        String userId = principal.getName();

        if (event.isTyping()) {
            presenceService.userStartedTyping(userId, roomId);
        } else {
            presenceService.userStoppedTyping(userId, roomId);
        }
    }

    /**
     * Heartbeat to keep user alive
     * Client sends: STOMP SEND to /app/presence/heartbeat
     * Should be sent every 30 seconds
     */
    @MessageMapping("/presence/heartbeat")
    public void heartbeat(Principal principal) {
        if (principal == null) {
            return;
        }

        String userId = principal.getName();
        presenceService.updateHeartbeat(userId);
    }
}
