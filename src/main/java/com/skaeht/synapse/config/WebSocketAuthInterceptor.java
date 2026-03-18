package com.skaeht.synapse.config;

import com.skaeht.synapse.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {

                String token = authHeader.substring(7);

                if (jwtTokenProvider.validateToken(token)) {

                    // Update: Extract email instead of username
                    String email = jwtTokenProvider.getEmailFromToken(token);

                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email, // Use email as the principal identifier
                                    null,
                                    Collections.emptyList()
                            );

                    accessor.setUser(authentication);

                    log.info("WebSocket authenticated user email: {}", email);
                }
            }
        }

        return message;
    }
}