package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.service.ChatService;
import com.skaeht.synapse.service.UserService;
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

    @Autowired
    private UserService userService;

    private Long getSenderId(String username) {
        return userService.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }
    /**
     * Handle messages sent to a specific room.
     * WebSocket destination: /app/room/{roomId}
     * 
     * @param roomId      The target room ID
     * @param chatMessage The message payload
     * @param principal   The authenticated user
     */
    @MessageMapping("/room/{roomId}")
    public void sendToRoom(@DestinationVariable String roomId, @Payload ChatMessage chatMessage, Principal principal) {
        String username = principal != null ? principal.getName() : chatMessage.from();
        Long senderId = getSenderId(username);
        log.info("User {} sending message to room {}", username, roomId);
        chatService.sendMessage(chatMessage.content(), senderId, username, roomId);
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
        Long senderId = getSenderId(username);
        log.info("User {} sending message to general room", username);
        chatService.sendMessage(chatMessage.content(), senderId, username);
    }

    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessage message, Principal principal) {
        String username = principal.getName();
        Long senderId = getSenderId(username);

        message = new ChatMessage(
                message.id(), message.roomId(), senderId, username,
                message.content(), message.timestamp(), message.traceId());

        chatService.sendMessage(message.content(), senderId, username, message.roomId());
    }
}