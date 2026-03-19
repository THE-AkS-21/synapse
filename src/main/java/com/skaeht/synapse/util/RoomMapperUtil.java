package com.skaeht.synapse.util;

import com.skaeht.synapse.dto.response.RoomResponse;
import com.skaeht.synapse.entity.Room;

import java.util.Collections;

/**
 * Utility class for mapping Room entities to concrete DTOs.
 */
public class RoomMapperUtil {

    public static RoomResponse toRoomResponse(Room room) {
        if (room == null) return null;

        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .type(room.getType() != null ? room.getType().name() : null)
                .creatorId(room.getCreator() != null ? room.getCreator().getId() : null)
                .theme(room.getTheme())
                .participants(room.getParticipants() != null ?
                        room.getParticipants().stream()
                                .map(p -> RoomResponse.RoomParticipantResponse.builder()
                                        .id(p.getId())
                                        .username(p.getUsername())
                                        .displayId(p.getDisplayId())
                                        .build())
                                .toList()
                        : Collections.emptyList())
                .build();
    }
}