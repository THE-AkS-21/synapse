package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<ChatMessage>> getRoomHistory(@PathVariable String roomId) {
        List<Message> messages = messageRepository.findByRoomIdOrderByTimestampDesc(roomId);

        // Extract unique sender IDs to efficiently fetch users
        List<Long> senderIds = messages.stream().map(Message::getSenderId).distinct().toList();

        // Map sender IDs to usernames
        Map<Long, String> userIdToUsername = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // Convert raw DB Messages to ChatMessage DTOs (which include both ID and Username)
        List<ChatMessage> chatMessages = messages.stream().map(msg -> new ChatMessage(
                msg.getMessageId(),
                msg.getRoomId(),
                msg.getSenderId(),
                userIdToUsername.getOrDefault(msg.getSenderId(), "Unknown User"), // Safe fallback
                msg.getContent(),
                msg.getTimestamp()
        )).toList();

        return ResponseEntity.ok(chatMessages);
    }
}