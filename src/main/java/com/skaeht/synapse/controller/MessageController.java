package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.event.ChatMessage;
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
        ).collect(Collectors.toList());

        return ResponseEntity.ok(chatMessages);
    }
}