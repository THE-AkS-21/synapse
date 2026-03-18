package com.skaeht.synapse.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
        private String id;
        private String roomId;
        private Long senderId;
        private String senderUsername;
        private String receiverUsername;
        private String content;
        private long timestamp;
        private String traceId;

        public ChatMessage(String roomId, Long senderId, String senderUsername, String content, long timestamp) {
                this.id = UUID.randomUUID().toString();
                this.roomId = roomId;
                this.senderId = senderId;
                this.senderUsername = senderUsername;
                this.content = content;
                this.timestamp = timestamp;
                this.traceId = UUID.randomUUID().toString();
        }
}