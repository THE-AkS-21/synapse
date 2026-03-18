package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.DirectMessage;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.DirectMessageRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

    private final DirectMessageRepository dmRepository;
    private final UserRepository userRepository;

    @Transactional
    public DirectMessage saveDirectMessage(Long senderId, Long receiverId, String content) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));

        DirectMessage msg = DirectMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .content(content)
                .isDeleted(false) // Default to visible
                .build();

        return dmRepository.save(msg);
    }

    public List<DirectMessage> getConversation(Long userId1, Long userId2) {
        User u1 = userRepository.findById(userId1).orElseThrow();
        User u2 = userRepository.findById(userId2).orElseThrow();
        return dmRepository.findConversation(u1, u2);
    }

    /**
     * SOFT DELETE IMPL: DM Deletion
     */
    @Transactional
    public DirectMessage softDeleteMessage(Long messageId, Long requesterId) {
        DirectMessage msg = dmRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        // For DMs, strictly only the sender can delete their own message
        if (!msg.getSender().getId().equals(requesterId)) {
            throw new SecurityException("Only the sender can delete a direct message");
        }

        msg.setDeleted(true);
        msg.setContent(""); // Scrub the payload
        return dmRepository.save(msg);
    }
}