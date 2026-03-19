package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RedisPublisher redisPublisher;

    @Transactional
    public Message saveMessage(ChatMessage chatMessage) {
        User sender = userRepository.findById(chatMessage.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        Room room = roomRepository.findById(chatMessage.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        Message message = Message.builder()
                .messageId(chatMessage.getId() != null ? chatMessage.getId() : UUID.randomUUID().toString())
                .room(room)
                .sender(sender)
                .content(chatMessage.getContent())
                .timestamp(chatMessage.getTimestamp())
                .isDeleted(false)
                .build();

        return messageRepository.save(message);
    }

    public List<Message> getRoomMessages(String roomId) {
        return messageRepository.findByRoomIdOrderByTimestampAsc(roomId);
    }

    @Transactional
    public Message softDeleteMessage(Long messageId, Long requesterId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!message.getSender().getId().equals(requesterId) &&
                !message.getRoom().getCreator().getId().equals(requesterId)) {
            throw new SecurityException("Not authorized to delete this message");
        }

        message.setDeleted(true);
        message.setContent("");
        Message saved = messageRepository.save(message);

        ChatMessage deleteEvent = new ChatMessage(
                message.getRoom().getId(), 0L, "SYSTEM", "MESSAGE_DELETED:" + messageId, System.currentTimeMillis()
        );
        redisPublisher.publish(deleteEvent);

        return saved;
    }
}