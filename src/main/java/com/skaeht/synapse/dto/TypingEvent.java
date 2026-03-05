package com.skaeht.synapse.dto;

/**
 * DTO for typing events sent by clients
 */
public record TypingEvent(
        boolean isTyping) {
}
