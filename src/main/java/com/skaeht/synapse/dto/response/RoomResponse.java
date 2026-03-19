package com.skaeht.synapse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Standardized data transfer object for Room payloads.
 */
@Data
@Builder
public class RoomResponse {
    private String id;
    private String name;
    private String type;
    private Long creatorId;
    private String theme;
    private List<RoomParticipantResponse> participants;

    /**
     * Nested DTO to ensure we only expose safe, public-facing user data
     * within the context of a room.
     */
    @Data
    @Builder
    public static class RoomParticipantResponse {
        private Long id;
        private String username;
        private String displayId;
    }
}