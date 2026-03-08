package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisPublisher with room-based channel routing
 */
@ExtendWith(MockitoExtension.class)
class RedisPublisherTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private RedisPublisher redisPublisher;

    private ChatMessage testMessage;

    @BeforeEach
    void setUp() {
        testMessage = new ChatMessage("general",1L, "testuser", "Hello, World!", System.currentTimeMillis());
    }

    @Test
    void testPublish_Success() throws Exception {
        // Act
        redisPublisher.publish(testMessage).join();

        // Assert - verify message was published to correct room channel
        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

        verify(redisTemplate, times(1)).convertAndSend(channelCaptor.capture(), messageCaptor.capture());

        // Verify channel is room-specific
        assertEquals("chat.room.general", channelCaptor.getValue());
        assertEquals(testMessage.content(), messageCaptor.getValue().content());
        assertEquals(testMessage.from(), messageCaptor.getValue().from());
    }

    @Test
    void testPublish_RedisError() {
        // Arrange
        doThrow(new RuntimeException("Redis connection error"))
                .when(redisTemplate).convertAndSend(anyString(), any());

        // Act & Assert
        assertThrows(Exception.class, () -> redisPublisher.publish(testMessage).join());
    }

    @Test
    void testPublish_DifferentRoom() throws Exception {
        // Arrange
        ChatMessage roomMessage = new ChatMessage("tech-talk",1L, "testuser", "Test message", System.currentTimeMillis());

        // Act
        redisPublisher.publish(roomMessage).join();

        // Assert
        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(1)).convertAndSend(channelCaptor.capture(), any(ChatMessage.class));

        assertEquals("chat.room.tech-talk", channelCaptor.getValue());
    }

    @Test
    void testPublish_NullRoomId() throws Exception {
        // Arrange - message with null roomId should default to "general"
        ChatMessage nullRoomMessage = new ChatMessage(null,1L, "testuser", "Test", System.currentTimeMillis());

        // Act
        redisPublisher.publish(nullRoomMessage).join();

        // Assert
        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(1)).convertAndSend(channelCaptor.capture(), any(ChatMessage.class));

        assertEquals("chat.room.general", channelCaptor.getValue());
    }
}
