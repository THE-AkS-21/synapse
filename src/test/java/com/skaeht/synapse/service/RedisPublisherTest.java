package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
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
 * Unit tests for RedisPublisher.
 * * ARCHITECTURAL REFERENCE:
 * This suite verifies the "Fan-Out" edge of our distributed WebSocket architecture.
 * It ensures that messages are correctly routed to their specific Redis Pub/Sub channels
 * based on the room ID, which is critical for preventing cross-room data leakage in a
 * multi-node deployment.
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
        testMessage = new ChatMessage("general", 1L, "testuser", "Hello, World!", System.currentTimeMillis());
    }

    @Test
    void testPublish_Success() {
        // Act
        redisPublisher.publish(testMessage).join();

        // Assert - Verify channel isolation and payload integrity
        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

        verify(redisTemplate, times(1)).convertAndSend(channelCaptor.capture(), messageCaptor.capture());

        assertEquals("chat.room.general", channelCaptor.getValue());
        assertEquals(testMessage.getContent(), messageCaptor.getValue().getContent());
        assertEquals(testMessage.getSenderUsername(), messageCaptor.getValue().getSenderUsername());
    }

    @Test
    void testPublish_RedisError_ShouldFailGracefully() {
        // Simulate a Redis network partition or timeout
        doThrow(new RuntimeException("Redis connection error"))
                .when(redisTemplate).convertAndSend(anyString(), any());

        // Ensure the CompletableFuture completes exceptionally rather than crashing the thread
        CompletableFuture<Void> future = redisPublisher.publish(testMessage);
        assertThrows(Exception.class, future::join);
    }

    @Test
    void testPublish_DifferentRoom_ShouldRouteToSpecificChannel() {
        ChatMessage roomMessage = new ChatMessage("tech-talk", 1L, "testuser", "Test message", System.currentTimeMillis());

        redisPublisher.publish(roomMessage).join();

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(1)).convertAndSend(channelCaptor.capture(), any(ChatMessage.class));

        assertEquals("chat.room.tech-talk", channelCaptor.getValue());
    }

    @Test
    void testPublish_NullRoomId_ShouldFallbackToGeneral() {
        // Null or malformed room IDs should default to a safe fallback channel
        // to prevent dynamic topic injection attacks or NullPointerExceptions.
        ChatMessage nullRoomMessage = new ChatMessage(null, 1L, "testuser", "Test", System.currentTimeMillis());

        redisPublisher.publish(nullRoomMessage).join();

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(1)).convertAndSend(channelCaptor.capture(), any(ChatMessage.class));

        assertEquals("chat.room.general", channelCaptor.getValue());
    }
}