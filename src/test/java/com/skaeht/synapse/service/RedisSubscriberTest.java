package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisSubscriber with room-based WebSocket routing
 */
@ExtendWith(MockitoExtension.class)
class RedisSubscriberTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RedisSubscriber redisSubscriber;

    private ChatMessage testChatMessage;
    private Message redisMessage;

    @BeforeEach
    void setUp() {
        testChatMessage = new ChatMessage("general",1L, "testuser", "Hello, World!", System.currentTimeMillis());
        redisMessage = mock(Message.class);
    }

    @Test
    void testOnMessage_Success() throws Exception {
        // Arrange
        byte[] messageBody = "message body".getBytes();
        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(messageBody, ChatMessage.class)).thenReturn(testChatMessage);

        // Act
        redisSubscriber.onMessage(redisMessage, null);

        // Assert - verify message forwarded to room-specific WebSocket destination
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);

        verify(messagingTemplate, times(1)).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());

        // Verify destination is room-specific
        assertEquals("/topic/chat/general", destinationCaptor.getValue());
        assertEquals(testChatMessage, messageCaptor.getValue());
    }

    @Test
    void testOnMessage_DeserializationError() throws Exception {
        // Arrange
        byte[] messageBody = "invalid json".getBytes();
        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(messageBody, ChatMessage.class))
                .thenThrow(new RuntimeException("Deserialization error"));

        // Act
        redisSubscriber.onMessage(redisMessage, null);

        // Assert - message should not be forwarded on error
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void testOnMessage_DifferentRoom() throws Exception {
        // Arrange
        ChatMessage techTalkMessage = new ChatMessage("tech-talk",1L, "user1", "Test", System.currentTimeMillis());
        byte[] messageBody = "message".getBytes();
        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(messageBody, ChatMessage.class)).thenReturn(techTalkMessage);

        // Act
        redisSubscriber.onMessage(redisMessage, null);

        // Assert
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);

        verify(messagingTemplate, times(1)).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());

        assertEquals("/topic/chat/tech-talk", destinationCaptor.getValue());
    }

    @Test
    void testOnMessage_NullRoomId() throws Exception {
        // Arrange - null roomId should default to "general"
        ChatMessage nullRoomMessage = new ChatMessage(null,1L, "user1", "Test", System.currentTimeMillis());
        byte[] messageBody = "message".getBytes();
        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(messageBody, ChatMessage.class)).thenReturn(nullRoomMessage);

        // Act
        redisSubscriber.onMessage(redisMessage, null);

        // Assert
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);

        verify(messagingTemplate, times(1)).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());

        assertEquals("/topic/chat/general", destinationCaptor.getValue());
    }
}
