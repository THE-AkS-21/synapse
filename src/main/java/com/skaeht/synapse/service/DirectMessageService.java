package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.DirectMessage;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.DirectMessageRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing private 1-to-1 conversations.
 * Isolated from the main Room architecture to allow for strict privacy controls,
 * end-to-end encryption prep, and dedicated query patterns.
 */
@Service
@RequiredArgsConstructor
public class DirectMessageService {

    private final DirectMessageRepository dmRepository;
    private final UserRepository userRepository;

    @Transactional
    public DirectMessage saveDirectMessage(Long senderId, Long receiverId, String content) {

        // PERFORMANCE FIX: Use getReferenceById to construct proxy entities.
        // We only need the IDs to satisfy the foreign key constraint, we don't need to load the full user profiles.
        User sender = userRepository.getReferenceById(senderId);
        User receiver = userRepository.getReferenceById(receiverId);

        DirectMessage msg = DirectMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .content(content)
                .isDeleted(false) // Default state for frontend rendering checks
                .build();

        return dmRepository.save(msg);
    }

    public List<DirectMessage> getConversation(Long userId1, Long userId2) {
        // PERFORMANCE FIX: Prevent N+1 select fetches just to map query parameters.
        User u1 = userRepository.getReferenceById(userId1);
        User u2 = userRepository.getReferenceById(userId2);

        return dmRepository.findConversation(u1, u2);
    }

    /**
     * SOFT DELETE ARCHITECTURE:
     * We never execute SQL DELETE statements on chat tables. Instead, we flip a boolean flag
     * and overwrite the content payload.
     * Why?
     * 1. Preserves referential integrity for message replies/threading.
     * 2. Allows the UI to render "This message was deleted" placeholders naturally.
     */
    @Transactional
    public DirectMessage softDeleteMessage(Long messageId, Long requesterId) {
        DirectMessage msg = dmRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!msg.getSender().getId().equals(requesterId)) {
            throw new SecurityException("Security Violation: User attempted to delete a DM they do not own.");
        }

        msg.setDeleted(true);
        msg.setContent(""); // Scrub the database payload to ensure permanent deletion of data
        return dmRepository.save(msg);
    }
}