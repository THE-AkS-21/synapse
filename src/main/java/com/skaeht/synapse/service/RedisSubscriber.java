package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.event.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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
            ChatMessage chatMessage = objectMapper.readValue(message.getBody(), ChatMessage.class);

            log.info("Received message from Redis: {} for room: {}",
                    chatMessage.getContent(), chatMessage.getRoomId());

            String destination = getWebSocketDestination(chatMessage.getRoomId());
            messagingTemplate.convertAndSend(destination, chatMessage);

            log.debug("Forwarded message {} to WebSocket destination: {}",
                    chatMessage.getId(), destination);

        } catch (Exception e) {
            log.error("Error processing message from Redis: {}", e.getMessage(), e);
        }
    }

    private String getWebSocketDestination(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return WEBSOCKET_TOPIC_PREFIX + "general";
        }
        return WEBSOCKET_TOPIC_PREFIX + roomId;
    }
}