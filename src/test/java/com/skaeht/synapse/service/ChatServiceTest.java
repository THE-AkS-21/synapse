package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.ChatMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatService with room-based messaging
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RedisPublisher redisPublisher;

    // Redis mocks for rate limiting
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RoomService roomService;


    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ChatService chatService;

    private String testUsername;
    private String testContent;

    @BeforeEach
    void setUp() {
        testUsername = "testuser";
        testContent = "Hello, World!";

        lenient().when(redisPublisher.publish(any(ChatMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // leniently mock Redis rate limiting to allow all messages in tests
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(anyString())).thenReturn(1L);
    }

    @Test
    void testSendMessage_Success() throws Exception {
        // Arrange
        String content = "Hello, World!";
        String sender = "testuser";
        String roomId = "general";
        Message mockMessage = Message.builder()
                .id(1L)
                .messageId("test-id")
                .roomId(roomId)
                .senderId(1L)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();

        when(messageRepository.save(any(Message.class))).thenReturn(mockMessage);

        // Act
        CompletableFuture<ChatMessage> result = chatService.sendMessage(content, 1L, sender, roomId);
        ChatMessage chatMessage = result.join();

        // Assert
        assertNotNull(chatMessage);
        assertEquals(content, chatMessage.content());
        assertEquals(sender, chatMessage.from());
        assertEquals(roomId, chatMessage.roomId());
        assertNotNull(chatMessage.id());
        assertNotNull(chatMessage.traceId());

        verify(messageRepository, times(1)).save(any(Message.class));
        verify(redisPublisher, times(1)).publish(any(ChatMessage.class));
    }

    @Test
    void testSendMessage_DefaultRoom() throws Exception {
        // Arrange
        String content = "Test message";
        String sender = "user1";

        when(messageRepository.save(any(Message.class))).thenReturn(Message.builder().build());

        // Act
        CompletableFuture<ChatMessage> result = chatService.sendMessage(content, 1L, sender);
        ChatMessage chatMessage = result.join();

        // Assert
        assertNotNull(chatMessage);
        assertEquals("general", chatMessage.roomId()); // Should default to "general"

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertEquals("general", messageCaptor.getValue().getRoomId());
    }

    @Test
    void testSendMessage_EmptyContent() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            chatService.sendMessage("", 1L, "testuser", "general");
        });

        verify(messageRepository, never()).save(any());
        verify(redisPublisher, never()).publish(any());
    }

    @Test
    void testSendMessage_NullContent() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            chatService.sendMessage(null, 1L, "testuser", "general");
        });

        verify(messageRepository, never()).save(any());
        verify(redisPublisher, never()).publish(any());
    }

    @Test
    void testSendMessage_ContentTooLong() {
        // Arrange
        String longContent = "a".repeat(5001);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            chatService.sendMessage(longContent,1L, "testuser", "general");
        });

        verify(messageRepository, never()).save(any());
        verify(redisPublisher, never()).publish(any());
    }

    @Test
    void testValidMessage_Valid() {
        // Assert
        assertTrue(chatService.isValidMessage("Hello"));
        assertTrue(chatService.isValidMessage("a".repeat(5000)));
    }

    @Test
    void testValidMessage_Invalid() {
        // Assert
        assertFalse(chatService.isValidMessage(null));
        assertFalse(chatService.isValidMessage(""));
        assertFalse(chatService.isValidMessage("   "));
        assertFalse(chatService.isValidMessage("a".repeat(5001)));
    }

    @Test
    void testSendMessage_VerifyMessageIdGeneration() throws Exception {
        // Arrange
        when(messageRepository.save(any(Message.class))).thenReturn(Message.builder().build());

        // Act
        ChatMessage message1 = chatService.sendMessage("Test 1", 1L, "user1", "room1").join();
        ChatMessage message2 = chatService.sendMessage("Test 2", 1L, "user1", "room1").join();

        // Assert - each message should have unique ID and traceId
        assertNotNull(message1.id());
        assertNotNull(message2.id());
        assertNotEquals(message1.id(), message2.id());

        assertNotNull(message1.traceId());
        assertNotNull(message2.traceId());
        assertNotEquals(message1.traceId(), message2.traceId());
    }
}
