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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private MessageRepository messageRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private RedisPublisher redisPublisher;
    @Mock private MessageBufferService messageBufferService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(redisTemplate, messageRepository, roomRepository, userRepository, redisPublisher, messageBufferService);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void sendMessage_Success() throws Exception {
        String content = "Hello World";
        Long senderId = 1L;
        String senderUsername = "user1";
        String roomId = "room1";

        when(valueOperations.increment(anyString())).thenReturn(1L);

        CompletableFuture<ChatMessage> future = chatService.sendMessage(content, senderId, senderUsername, roomId);
        ChatMessage result = future.get();

        assertNotNull(result);
        assertEquals(content, result.getContent());
        assertEquals(roomId, result.getRoomId());
        assertEquals(senderId, result.getSenderId());

        // Verifies event is published and buffered for async database save
        verify(redisPublisher, times(1)).publish(any(ChatMessage.class));
        verify(messageBufferService, times(1)).bufferMessage(any(ChatMessage.class));
    }

    @Test
    void sendMessage_RateLimited() {
        when(valueOperations.increment(anyString())).thenReturn(6L);
        assertThrows(IllegalStateException.class, () -> {
            chatService.sendMessage("spam", 1L, "user1", "room1");
        });
    }
}