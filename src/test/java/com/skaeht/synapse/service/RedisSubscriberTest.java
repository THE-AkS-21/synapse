package com.skaeht.synapse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.event.ChatMessage;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisSubscriber.
 * * ARCHITECTURAL REFERENCE:
 * This suite verifies the "Fan-In" edge of the distributed architecture.
 * It ensures that when a message is picked up from the Redis backbone, it is
 * safely deserialized and blasted down to the local hardware's open WebSocket
 * connections using the SimpMessagingTemplate.
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
        testChatMessage = new ChatMessage("general", 1L, "testuser", "Hello, World!", System.currentTimeMillis());
        redisMessage = mock(Message.class);
    }

    @Test
    void testOnMessage_Success() throws Exception {
        byte[] messageBody = "message body".getBytes();
        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(messageBody, ChatMessage.class)).thenReturn(testChatMessage);

        redisSubscriber.onMessage(redisMessage, null);

        // Verify the payload is forwarded to the correct local STOMP broker destination
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);

        verify(messagingTemplate, times(1)).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());

        assertEquals("/topic/chat/general", destinationCaptor.getValue());
        assertEquals(testChatMessage, messageCaptor.getValue());
    }

    @Test
    void testOnMessage_DeserializationError_ShouldDropMessage() throws Exception {
        // Verifies the "Poison Pill" scenario where corrupted Redis data does not crash the listener loop.
        byte[] messageBody = "invalid json".getBytes();
        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(messageBody, ChatMessage.class))
                .thenThrow(new RuntimeException("Deserialization error"));

        redisSubscriber.onMessage(redisMessage, null);

        // The listener should swallow the error and NOT attempt to forward a corrupted payload
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void testOnMessage_DifferentRoom() throws Exception {
        ChatMessage techTalkMessage = new ChatMessage("tech-talk", 1L, "user1", "Test", System.currentTimeMillis());
        byte[] messageBody = "message".getBytes();
        when(redisMessage.getBody()).thenReturn(messageBody);
        when(objectMapper.readValue(messageBody, ChatMessage.class)).thenReturn(techTalkMessage);

        redisSubscriber.onMessage(redisMessage, null);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, times(1)).convertAndSend(destinationCaptor.capture(), any(Object.class));

        assertEquals("/topic/chat/tech-talk", destinationCaptor.getValue());
    }
}