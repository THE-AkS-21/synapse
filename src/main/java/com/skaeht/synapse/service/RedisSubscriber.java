package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for subscribing to Redis channels and forwarding messages to
 * WebSocket clients.
 * Supports room-based message routing to specific WebSocket destinations.
 */
@Service
@Slf4j
public class RedisSubscriber implements MessageListener {

    private static final String WEBSOCKET_TOPIC_PREFIX = "/topic/chat/";

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Deserialize the message
            ChatMessage chatMessage = objectMapper.readValue(message.getBody(), ChatMessage.class);

            log.info("Received message from Redis: {} for room: {}",
                    chatMessage.content(), chatMessage.roomId());

            // Extract room ID from message and forward to room-specific WebSocket topic
            String destination = getWebSocketDestination(chatMessage.roomId());
            messagingTemplate.convertAndSend(destination, chatMessage);

            log.debug("Forwarded message {} to WebSocket destination: {}",
                    chatMessage.id(), destination);

        } catch (Exception e) {
            log.error("Error processing message from Redis: {}", e.getMessage(), e);
        }
    }

    /**
     * Get the WebSocket destination for a given room ID
     */
    private String getWebSocketDestination(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return WEBSOCKET_TOPIC_PREFIX + "general";
        }
        return WEBSOCKET_TOPIC_PREFIX + roomId;
    }
}