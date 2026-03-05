package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageService.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageService messageService;

    private List<Message> testMessages;

    @BeforeEach
    void setUp() {
        testMessages = Arrays.asList(
                Message.builder().id(1L).senderUsername("user1").content("Message 1").timestamp(1000L).build(),
                Message.builder().id(2L).senderUsername("user2").content("Message 2").timestamp(2000L).build(),
                Message.builder().id(3L).senderUsername("user1").content("Message 3").timestamp(3000L).build());
    }

    @Test
    void testGetMessageHistory() {
        // Arrange
        Page<Message> expectedPage = new PageImpl<>(testMessages);
        when(messageRepository.findAll(any(Pageable.class))).thenReturn(expectedPage);

        // Act
        Page<Message> result = messageService.getMessageHistory(0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getContent().size());
        verify(messageRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void testGetRecentMessages() {
        // Arrange
        Page<Message> page = new PageImpl<>(testMessages);
        when(messageRepository.findAll(any(Pageable.class))).thenReturn(page);

        // Act
        List<Message> result = messageService.getRecentMessages(10);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        verify(messageRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void testGetMessagesBySender() {
        // Arrange
        List<Message> user1Messages = Arrays.asList(testMessages.get(0), testMessages.get(2));
        Page<Message> page = new PageImpl<>(user1Messages);
        when(messageRepository.findBySenderUsername(eq("user1"), any(Pageable.class))).thenReturn(page);

        // Act
        Page<Message> result = messageService.getMessagesBySender("user1", 0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("user1", result.getContent().get(0).getSenderUsername());
        verify(messageRepository, times(1)).findBySenderUsername(eq("user1"), any(Pageable.class));
    }

    @Test
    void testGetMessageById_Found() {
        // Arrange
        Message message = testMessages.get(0);
        when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

        // Act
        Optional<Message> result = messageService.getMessageById(1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        verify(messageRepository, times(1)).findById(1L);
    }

    @Test
    void testGetMessageById_NotFound() {
        // Arrange
        when(messageRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act
        Optional<Message> result = messageService.getMessageById(999L);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetTotalMessageCount() {
        // Arrange
        when(messageRepository.count()).thenReturn(100L);

        // Act
        long result = messageService.getTotalMessageCount();

        // Assert
        assertEquals(100L, result);
        verify(messageRepository, times(1)).count();
    }

    @Test
    void testDeleteOldMessages() {
        // Arrange
        long beforeTimestamp = 5000L;
        when(messageRepository.deleteByTimestampBefore(beforeTimestamp)).thenReturn(10L);

        // Act
        long result = messageService.deleteOldMessages(beforeTimestamp);

        // Assert
        assertEquals(10L, result);
        verify(messageRepository, times(1)).deleteByTimestampBefore(beforeTimestamp);
    }
}
