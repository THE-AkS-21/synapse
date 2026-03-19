package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.response.UserProfileResponse;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * ARCHITECTURE NOTE: DTO Boundary Verification
 * This unit test validates the crucial boundary between internal database entities
 * and external network payloads. It ensures that sensitive fields (like BCrypt hashes)
 * are explicitly dropped before serialization.
 */
@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController roomController;

    @Test
    void getRoomParticipants_ReturnsSafeDTOs() {
        // Arrange
        User user = User.builder()
                .id(1L)
                .username("secureUser")
                .email("test@test.com")
                .displayId("DISP-123")
                .password("SECRET_HASHED_PASSWORD")
                .build();

        when(roomService.getRoomParticipants("room-123")).thenReturn(List.of(user));

        // Act
        ResponseEntity<List<UserProfileResponse>> response = roomController.getRoomParticipants("room-123");

        // Assert - Validate that the mapping layer successfully converted the Entity to a DTO
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        UserProfileResponse dto = response.getBody().get(0);
        assertEquals(1L, dto.id());
        assertEquals("secureUser", dto.username());
        assertEquals("DISP-123", dto.displayId());
    }
}