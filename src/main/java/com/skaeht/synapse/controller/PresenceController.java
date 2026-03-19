package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.event.TypingEvent;
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
 * WebSocket controller managing user presence (online/offline status, typing indicators).
 * Enabled via application.properties (`presence.enabled=true`).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@ConditionalOnProperty(name = "presence.enabled", havingValue = "true", matchIfMissing = false)
public class PresenceController {

    private final PresenceService presenceService;

    @MessageMapping("/presence/join/{roomId}")
    public void joinRoom(@DestinationVariable String roomId, Principal principal) {
        if (isUnauthenticated(principal, "join room " + roomId)) return;
        presenceService.userJoinedRoom(principal.getName(), roomId);
    }

    @MessageMapping("/presence/leave/{roomId}")
    public void leaveRoom(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        presenceService.userLeftRoom(principal.getName(), roomId);
    }

    @MessageMapping("/presence/typing/{roomId}")
    public void typing(@DestinationVariable String roomId,
                       @Payload TypingEvent event,
                       Principal principal) {
        if (principal == null) return;

        String userId = principal.getName();
        if (event.isTyping()) {
            presenceService.userStartedTyping(userId, roomId);
        } else {
            presenceService.userStoppedTyping(userId, roomId);
        }
    }

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(Principal principal) {
        if (principal == null) return;
        presenceService.updateHeartbeat(principal.getName());
    }

    /** Helper to cleanly log and reject unauthenticated socket requests */
    private boolean isUnauthenticated(Principal principal, String action) {
        if (principal == null || principal.getName() == null) {
            log.warn("SECURITY: Unauthenticated user attempted to {}", action);
            return true;
        }
        return false;
    }
}