package com.skaeht.synapse.util;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.DirectMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.entity.User;

/**
 * Utility for mapping database message entities to WebSocket transfer objects.
 * Centralizing this ensures all outbound messages have a consistent structure.
 */
public class MessageMapperUtil {

    public static ChatMessage toRoomMessageDto(Message savedMsg) {
        return ChatMessage.builder()
                .id(savedMsg.getMessageId())
                .roomId(savedMsg.getRoom().getId())
                .senderId(savedMsg.getSender().getId())
                .senderUsername(savedMsg.getSender().getUsername())
                .content(savedMsg.getContent())
                .timestamp(savedMsg.getTimestamp())
                .build();
    }

    public static ChatMessage toDirectMessageDto(DirectMessage savedDm, User sender, User receiver) {
        return ChatMessage.builder()
                .id(String.valueOf(savedDm.getId()))
                .roomId("dm") // Identifier for client-side routing
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .receiverUsername(receiver.getUsername())
                .content(savedDm.getContent())
                .timestamp(savedDm.getTimestamp().toEpochMilli())
                .build();
    }
}