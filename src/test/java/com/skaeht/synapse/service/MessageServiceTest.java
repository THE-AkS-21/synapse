package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageService.
 * Validates the core CRUD operations for messages, particularly focusing on
 * the authorization logic surrounding soft deletions.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RedisPublisher redisPublisher;

    @InjectMocks private MessageService messageService;

    private User testUser;
    private Room testRoom;
    private Message testMessage;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).username("testuser").build();
        testRoom = Room.builder().id("room1").creator(testUser).build();
        testMessage = Message.builder().id(10L).sender(testUser).room(testRoom).content("Hello").isDeleted(false).build();
    }

    @Test
    void saveMessage_Success() {
        ChatMessage chatMsg = new ChatMessage("room1", 1L, "testuser", "Hello", System.currentTimeMillis());

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roomRepository.findById("room1")).thenReturn(Optional.of(testRoom));
        when(messageRepository.save(any(Message.class))).thenReturn(testMessage);

        Message saved = messageService.saveMessage(chatMsg);
        assertNotNull(saved);
        assertEquals("Hello", saved.getContent());
    }

    @Test
    void getRoomMessages_Success() {
        when(messageRepository.findByRoomIdOrderByTimestampAsc("room1")).thenReturn(List.of(testMessage));
        List<Message> messages = messageService.getRoomMessages("room1");
        assertFalse(messages.isEmpty());
        assertEquals(1, messages.size());
    }

    @Test
    void softDeleteMessage_Success() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(testMessage));
        when(messageRepository.save(any(Message.class))).thenReturn(testMessage);

        // testUser (1L) is both the sender and the room creator
        Message deleted = messageService.softDeleteMessage(10L, 1L);
        assertTrue(deleted.isDeleted());
        assertEquals("", deleted.getContent());
        // Verify the system message was published for active clients
        verify(redisPublisher, times(1)).publish(any(ChatMessage.class));
    }

    @Test
    void softDeleteMessage_Unauthorized() {
        User otherUser = User.builder().id(2L).build();
        testMessage.setSender(otherUser);

        // Creator is testUser (1L), sender is otherUser (2L). Requester is 3L (Unauthorized).
        when(messageRepository.findById(10L)).thenReturn(Optional.of(testMessage));

        assertThrows(SecurityException.class, () -> messageService.softDeleteMessage(10L, 3L));
    }
}