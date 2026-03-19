package com.skaeht.synapse.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ARCHITECTURE NOTE: STOMP Message Broker Pipeline
 * Configures the internal routing topography for all real-time bidirectional traffic.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    /**
     * Binds the custom JWT security interceptor to the inbound message channel,
     * ensuring that no unauthorized messages can enter the system.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }

    /**
     * Defines the internal routing semantics.
     * Clients subscribe to "/topic/..." to listen for broadcasts.
     * Clients send messages to "/app/..." which routes them to our @MessageMapping controllers.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue"); // Enabled /queue for direct 1-to-1 messaging
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user"); // Required for convertAndSendToUser capabilities
    }

    /**
     * Defines the physical entry point for the WebSocket handshake upgrade request.
     * SockJS is enabled as a fallback mechanism for restrictive corporate firewalls
     * that block native WebSocket upgrades.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}