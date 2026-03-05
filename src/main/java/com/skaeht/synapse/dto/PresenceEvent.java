package com.skaeht.synapse.dto;

import java.util.Set;

/**
 * DTO for presence events broadcasted to room participants.
 * Includes information about online users and typing users.
 */
public record PresenceEvent(
        String roomId,
        String userId,
        PresenceType type,
        long timestamp,
        Set<String> onlineUsers,
        Set<String> typingUsers) {
    /**
     * Create a simple presence event without user lists
     */
    public PresenceEvent(String roomId, String userId, PresenceType type, long timestamp) {
        this(roomId, userId, type, timestamp, null, null);
    }
}
