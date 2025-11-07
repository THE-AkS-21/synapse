package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.service.RedisPublisher; // <-- IMPORT THIS
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
// import org.springframework.messaging.handler.annotation.SendTo; // <-- REMOVE THIS
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatController {

    @Autowired
    private MessageRepository messageRepository;

    // --- ADD THIS ---
    @Autowired
    private RedisPublisher redisPublisher;

    @MessageMapping("/chat.sendMessage")
    // --- REMOVE @SendTo ---
    // @SendTo("/topic/public")
    public void sendMessage(@Payload ChatMessage chatMessage, Principal principal) {

        String username = principal.getName();

        ChatMessage messageToSaveAndSend = new ChatMessage(
                username,
                chatMessage.content(),
                System.currentTimeMillis()
        );

        // 1. Save to database
        Message dbMessage = Message.builder()
                .senderUsername(messageToSaveAndSend.from())
                .content(messageToSaveAndSend.content())
                .timestamp(messageToSaveAndSend.timestamp())
                .build();

        messageRepository.save(dbMessage);

        // 2. Publish to Redis
        // This will be picked up by all server instances
        redisPublisher.publish(messageToSaveAndSend);

        // We no longer return the message here
        // return messageToSaveAndSend; // <-- REMOVE THIS
    }
}