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
    void clearRoomMessages_Success() {
        when(roomRepository.findById("room1")).thenReturn(Optional.of(testRoom));
        doNothing().when(messageRepository).deleteByRoomId("room1");

        // Requester (1L) is the creator of the room
        messageService.clearRoomMessages("room1", 1L);

        // Verify DB was wiped and Redis published the cleared event
        verify(messageRepository, times(1)).deleteByRoomId("room1");
        verify(redisPublisher, times(1)).publish(argThat(msg ->
                msg.getContent().equals("MESSAGES_CLEARED") && msg.getRoomId().equals("room1")));
    }

    @Test
    void clearRoomMessages_Unauthorized() {
        when(roomRepository.findById("room1")).thenReturn(Optional.of(testRoom));

        // Requester 3L is not the creator of testRoom (which belongs to 1L)
        assertThrows(SecurityException.class, () -> messageService.clearRoomMessages("room1", 3L));

        // Verify the database delete was never called due to the security exception
        verify(messageRepository, never()).deleteByRoomId(anyString());
        verify(redisPublisher, never()).publish(any(ChatMessage.class));
    }
}