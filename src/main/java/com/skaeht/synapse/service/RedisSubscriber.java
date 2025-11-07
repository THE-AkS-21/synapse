package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisSubscriber.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate; // For sending to WebSocket clients

    @Autowired
    private ObjectMapper objectMapper; // For deserializing the message from Redis

    // This is the method name we specified in RedisConfig ("onMessage")
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Deserialize the JSON message from Redis into our ChatMessage object
            ChatMessage chatMessage = objectMapper.readValue(message.getBody(), ChatMessage.class);

            log.info("Received message from Redis: {}", chatMessage.content());

            // Broadcast the message to all local WebSocket clients
            // who are subscribed to "/topic/public"
            messagingTemplate.convertAndSend("/topic/public", chatMessage);

        } catch (IOException e) {
            log.error("Could not deserialize Redis message", e);
        }
    }
}