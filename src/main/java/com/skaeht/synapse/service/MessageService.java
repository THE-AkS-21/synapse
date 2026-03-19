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

    /**
     * Persists a new message to the database.
     * Uses UUID for the message ID if one is not provided by the client, ensuring client-side
     * message generation compatibility.
     *
     * @param chatMessage The DTO containing message details.
     * @return The persisted Message entity.
     * @throws IllegalArgumentException if the sender or room cannot be found.
     */
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

    /**
     * Retrieves all messages for a specific room, ordered chronologically.
     * Used for initial room load history.
     */
    public List<Message> getRoomMessages(String roomId) {
        return messageRepository.findByRoomIdOrderByTimestampAsc(roomId);
    }

    /**
     * Performs a soft delete on a message.
     * Instead of removing the row from the database, it clears the content, sets the isDeleted flag,
     * and broadcasts a deletion event so active clients can update their UI in real-time.
     * Only the original sender or the room creator can delete a message.
     *
     * @param messageId The internal database ID of the message.
     * @param requesterId The ID of the user requesting the deletion.
     * @return The updated Message entity.
     * @throws SecurityException if the requester is neither the sender nor the room creator.
     */
    @Transactional
    public Message softDeleteMessage(String messageId, Long requesterId) {
        Message message = messageRepository.findByMessageId(messageId)
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

    @Transactional
    public void clearRoomMessages(String roomId, Long requesterId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        // Authorization: Only the creator or participants in a DM can clear
        if (room.getType() != Room.RoomType.DIRECT && room.getCreator() != null && !room.getCreator().getId().equals(requesterId)) {
            throw new SecurityException("Only the room creator can clear messages");
        }

        // Delete from Database
        messageRepository.deleteByRoomId(roomId);

        // Broadcast to WebSocket clients so participants see the empty screen instantly
        ChatMessage systemEvent = new ChatMessage(
                roomId, 0L, "SYSTEM", "MESSAGES_CLEARED", System.currentTimeMillis()
        );
        redisPublisher.publish(systemEvent);
    }
}