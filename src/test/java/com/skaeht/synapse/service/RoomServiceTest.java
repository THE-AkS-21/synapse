package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageRepository messageRepository;

    @InjectMocks private RoomService roomService;

    private User creator;
    private Room publicRoom;

    @BeforeEach
    void setUp() {
        // Ensure email is set so our removeMember test passes the authorization check
        creator = User.builder()
                .id(1L)
                .username("creator")
                .email("creator@test.com")
                .build();

        publicRoom = Room.builder()
                .id("room1")
                .name("General")
                .type(Room.RoomType.PUBLIC)
                .creator(creator)
                .participants(new HashSet<>())
                .build();
    }

    @Test
    void createRoom_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(roomRepository.save(any(Room.class))).thenReturn(publicRoom);

        Room room = roomService.createRoom("General", Room.RoomType.PUBLIC, 1L);
        assertNotNull(room);
        assertEquals("General", room.getName());
    }

    @Test
    void getPublicRooms_Success() {
        when(roomRepository.findByType(Room.RoomType.PUBLIC)).thenReturn(List.of(publicRoom));
        List<Room> rooms = roomService.getPublicRooms();
        assertEquals(1, rooms.size());
    }

    @Test
    void addParticipant_Success() {
        User newUser = User.builder().id(2L).username("newuser").build();
        when(roomRepository.findById("room1")).thenReturn(Optional.of(publicRoom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newUser));

        roomService.addParticipant("room1", 2L);
        verify(roomRepository, times(1)).save(publicRoom);
        assertTrue(publicRoom.getParticipants().contains(newUser));
    }

    @Test
    void removeMember_Success() {
        // Arrange
        User memberToRemove = User.builder().id(2L).username("member").build();
        publicRoom.getParticipants().add(creator);
        publicRoom.getParticipants().add(memberToRemove);

        when(roomRepository.findById("room1")).thenReturn(Optional.of(publicRoom));
        // Note: RoomService.removeMember uses findByEmail for the requester
        when(userRepository.findByEmail("creator@test.com")).thenReturn(Optional.of(creator));
        when(userRepository.findById(2L)).thenReturn(Optional.of(memberToRemove));

        // Act - Call the correct method signature: removeMember(roomId, userId, requesterEmail)
        roomService.removeMember("room1", 2L, "creator@test.com");

        // Assert
        assertFalse(publicRoom.getParticipants().contains(memberToRemove));
        verify(roomRepository, times(1)).save(publicRoom);
    }

    @Test
    void deleteRoom_Success() {
        // Arrange
        when(roomRepository.findById("room1")).thenReturn(Optional.of(publicRoom));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));

        // Act
        roomService.deleteRoom("room1", 1L);

        // Assert
        verify(roomRepository, times(1)).delete(publicRoom);
    }

    @Test
    void updateTheme_Success() {
        when(roomRepository.findById("room1")).thenReturn(Optional.of(publicRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(publicRoom);

        Room updated = roomService.updateTheme("room1", "dark", 1L);
        assertEquals("dark", updated.getTheme());
    }
}