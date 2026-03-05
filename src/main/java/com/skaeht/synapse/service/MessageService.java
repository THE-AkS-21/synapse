package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing message operations including retrieval and history.
 */
@Service
@Transactional(readOnly = true)
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    /**
     * Get message history with pagination.
     *
     * @param page Page number (0-indexed)
     * @param size Number of messages per page
     * @return Page of messages sorted by timestamp descending
     */
    public Page<Message> getMessageHistory(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return messageRepository.findAll(pageable);
    }

    /**
     * Get recent messages (last N messages).
     *
     * @param limit Maximum number of messages to retrieve
     * @return List of recent messages
     */
    public List<Message> getRecentMessages(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"));
        return messageRepository.findAll(pageable).getContent();
    }

    /**
     * Get messages by sender username.
     *
     * @param username The sender's username
     * @param page     Page number
     * @param size     Page size
     * @return Page of messages from the specified user
     */
    public Page<Message> getMessagesBySender(String username, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return messageRepository.findBySenderUsername(username, pageable);
    }

    /**
     * Get a specific message by ID.
     *
     * @param id The message ID
     * @return Optional containing the message if found
     */
    public Optional<Message> getMessageById(Long id) {
        return messageRepository.findById(id);
    }

    /**
     * Get total message count.
     *
     * @return Total number of messages
     */
    public long getTotalMessageCount() {
        return messageRepository.count();
    }

    /**
     * Delete old messages (cleanup operation).
     *
     * @param beforeTimestamp Delete messages older than this timestamp
     * @return Number of deleted messages
     */
    @Transactional
    public long deleteOldMessages(long beforeTimestamp) {
        return messageRepository.deleteByTimestampBefore(beforeTimestamp);
    }
}
