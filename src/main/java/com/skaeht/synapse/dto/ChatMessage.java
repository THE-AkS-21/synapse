package com.skaeht.synapse.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * DTO for chat messages with room-based routing and distributed tracing
 * support.
 * 
 * @param id        Unique message identifier for deduplication and tracking
 * @param roomId    Target room/channel ID for message routing
 * @param from      Username of the message sender
 * @param content   Message content
 * @param timestamp Unix timestamp in milliseconds
 * @param traceId   Distributed tracing identifier for debugging
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(
                String id,
                String roomId,
                String from,
                String content,
                long timestamp,
                String traceId) {
        /**
         * Constructor with auto-generated ID and trace ID
         */
        public ChatMessage(String roomId, String from, String content, long timestamp) {
                this(
                                UUID.randomUUID().toString(),
                                roomId,
                                from,
                                content,
                                timestamp,
                                UUID.randomUUID().toString());
        }

        /**
         * Constructor with manual ID but auto-generated trace ID
         */
        public ChatMessage(String id, String roomId, String from, String content, long timestamp) {
                this(id, roomId, from, content, timestamp, UUID.randomUUID().toString());
        }
}