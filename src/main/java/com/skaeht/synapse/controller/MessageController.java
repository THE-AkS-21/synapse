package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<ChatMessage>> getRoomHistory(@PathVariable String roomId) {
        List<Message> messages = messageRepository.findByRoomIdOrderByTimestampDesc(roomId);

        List<Long> senderIds = messages.stream().map(msg -> msg.getSender().getId()).distinct().toList();
        Map<Long, String> userIdToUsername = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        List<ChatMessage> chatMessages = messages.stream().map(msg -> ChatMessage.builder()
                .id(msg.getMessageId())
                .roomId(msg.getRoom().getId())
                .senderId(msg.getSender().getId())
                .senderUsername(userIdToUsername.getOrDefault(msg.getSender().getId(), "Unknown User"))
                .content(msg.getContent())
                .timestamp(msg.getTimestamp())
                .isDeleted(msg.isDeleted())
                .build()
        ).toList();

        return ResponseEntity.ok(chatMessages);
    }

    // Individual Soft Delete
    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable String messageId, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        messageService.softDeleteMessage(messageId, user.getId());
        return ResponseEntity.ok(Map.of("message", "Message deleted successfully"));
    }

    // CRITICAL FIX: Clear all room messages endpoint
    @DeleteMapping("/room/{roomId}")
    public ResponseEntity<?> clearRoomMessages(@PathVariable String roomId, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        messageService.clearRoomMessages(roomId, user.getId());
        return ResponseEntity.ok(Map.of("message", "All messages cleared successfully"));
    }
}