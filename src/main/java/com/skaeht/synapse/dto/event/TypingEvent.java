package com.skaeht.synapse.dto.event;

/**
 * DTO for typing events sent by clients
 */
public record TypingEvent(
        boolean isTyping) {
}
