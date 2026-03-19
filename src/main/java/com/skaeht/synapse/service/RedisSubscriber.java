package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.event.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * ARCHITECTURE NOTE: The Local Sink (Subscriber)
 * Listens to the Redis distributed event bus. Every active Spring Boot node runs this listener.
 * When an event arrives, it uses the local SimpMessagingTemplate to blast the message down
 * to any clients currently holding open WebSocket connections on this specific server hardware.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private static final String WEBSOCKET_TOPIC_PREFIX = "/topic/chat/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // ObjectMapper is heavily optimized for reuse in modern Jackson versions
            ChatMessage chatMessage = objectMapper.readValue(message.getBody(), ChatMessage.class);

            String destination = getWebSocketDestination(chatMessage.getRoomId());
            messagingTemplate.convertAndSend(destination, chatMessage);

        } catch (Exception e) {
            log.error("Corrupted payload received from Redis Pub/Sub", e);
        }
    }

    private String getWebSocketDestination(String roomId) {
        return WEBSOCKET_TOPIC_PREFIX + (roomId == null || roomId.isEmpty() ? "general" : roomId);
    }
}