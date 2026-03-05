package com.skaeht.synapse.controller;

import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    @Autowired
    private MessageRepository messageRepository;

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<Message>> getRoomHistory(@PathVariable String roomId) {
        return ResponseEntity.ok(messageRepository.findByRoomIdOrderByTimestampDesc(roomId));
    }
}
