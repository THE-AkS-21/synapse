package com.skaeht.synapse.config;

import com.skaeht.synapse.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * ARCHITECTURE NOTE: WebSocket Handshake Security
 * Standard HTTP interceptors (like JwtAuthFilter) cannot authorize the persistent STOMP
 * frames sent over an active WebSocket connection.
 * This ChannelInterceptor acts as a guard at the WebSocket entrance. When a client attempts
 * to send a STOMP "CONNECT" frame, this intercepts the frame, extracts the Bearer token
 * from the native headers, cryptographically validates it, and securely binds the user's
 * identity to the socket session for the duration of the connection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Guard clause: We only care about the initial connection handshake
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtTokenProvider.validateToken(token)) {
                String email = jwtTokenProvider.getEmailFromToken(token);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        Collections.emptyList()
                );

                accessor.setUser(authentication);
                log.debug("WebSocket TCP Connection Authenticated. Principal: {}", email);
            } else {
                log.warn("WebSocket TCP Connection Rejected. Reason: Invalid or Expired JWT");
                throw new IllegalArgumentException("Invalid JWT token provided in STOMP headers");
            }
        }

        return message;
    }
}