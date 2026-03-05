package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket controller for handling chat messages with room-based routing.
 * Supports sending messages to specific rooms and direct messages.
 */
@Controller
@Slf4j
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * Handle messages sent to a specific room.
     * WebSocket destination: /app/room/{roomId}
     * 
     * @param roomId      The target room ID
     * @param chatMessage The message payload
     * @param principal   The authenticated user
     */
    @MessageMapping("/room/{roomId}")
    public void sendToRoom(
            @DestinationVariable String roomId,
            @Payload ChatMessage chatMessage,
            Principal principal) {

        String username = principal != null ? principal.getName() : chatMessage.from();

        log.info("User {} sending message to room {}", username, roomId);

        // Send message via service (async)
        chatService.sendMessage(chatMessage.content(), username, roomId);
    }

    /**
     * Handle messages sent to the default/general room.
     * WebSocket destination: /app/chat
     * 
     * @param chatMessage The message payload
     * @param principal   The authenticated user
     */
    @MessageMapping("/chat")
    public void send(@Payload ChatMessage chatMessage, Principal principal) {
        String username = principal != null ? principal.getName() : chatMessage.from();

        log.info("User {} sending message to general room", username);

        // Send to default "general" room
        chatService.sendMessage(chatMessage.content(), username);
    }
}