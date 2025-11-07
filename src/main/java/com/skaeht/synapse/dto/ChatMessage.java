package com.skaeht.synapse.dto;

// This is the object for WebSocket communication
// It will also be serialized and published to Redis
public record ChatMessage(
        String from,
        String content,
        long timestamp
) {}