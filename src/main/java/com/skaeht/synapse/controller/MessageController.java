package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<ChatMessage>> getRoomHistory(@PathVariable String roomId) {
        List<Message> messages = messageRepository.findByRoomIdOrderByTimestampDesc(roomId);

        /*
         * Fetch all distinct senders in a single query to prevent N+1 database hits
         * when mapping hundreds of historical messages.
         */
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
                .build()
        ).toList();

        return ResponseEntity.ok(chatMessages);
    }
}