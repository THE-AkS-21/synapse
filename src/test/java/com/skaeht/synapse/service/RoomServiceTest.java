package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoomService
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoomService roomService;

    private User testUser1;
    private User testUser2;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        testUser1 = new User();
        testUser1.setId(1L);
        testUser1.setUsername("user1");

        testUser2 = new User();
        testUser2.setId(2L);
        testUser2.setUsername("user2");

        testRoom = Room.builder()
                .id(UUID.randomUUID().toString())
                .name("test-room")
                .type(Room.RoomType.PUBLIC)
                .build();
    }

    @Test
    void testCreateRoom_Success() {
        // Arrange
        when(roomRepository.existsByName("test_room")).thenReturn(false);
        // Map the new logic to return a placeholder Creator User
        User mockCreator = new User();
        mockCreator.setId(1L);
        mockCreator.setUsername("test_creator_username");
        when(userRepository.findByUsername("test_creator_username")).thenReturn(Optional.of(mockCreator));

        // Assuming 'room' refers to a Room object that would be returned by the save
        // method.
        // For a test, we can create a mock Room or use the argument passed to save.
        Room mockSavedRoom = Room.builder()
                .id(UUID.randomUUID().toString())
                .name("test_room")
                .type(Room.RoomType.PUBLIC)
                .build();
        when(roomRepository.save(any(Room.class))).thenReturn(mockSavedRoom);

        Room savedRoom = roomService.createRoom("test_room", Room.RoomType.PUBLIC, "test_creator_username");

        // Assert
        assertNotNull(savedRoom);
        assertEquals("test_room", savedRoom.getName());
        assertEquals(Room.RoomType.PUBLIC, savedRoom.getType());
        assertNotNull(savedRoom.getId());

        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void testCreateRoom_DuplicateName() {
        // Arrange
        when(roomRepository.existsByName("test_room")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> {
            roomService.createRoom("test_room", Room.RoomType.PUBLIC, "test_creator_username");
        });

        verify(roomRepository, never()).save(any());
    }

    @Test
    void testGetOrCreateDirectMessageRoom_NewRoom() {
        // Arrange
        when(roomRepository.findDirectMessageRoom(1L, 2L)).thenReturn(Optional.empty());
        when(roomRepository.findDirectMessageRoom(2L, 1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Room dmRoom = roomService.getOrCreateDirectMessageRoom(1L, 2L);

        // Assert
        assertNotNull(dmRoom);
        assertEquals(Room.RoomType.DIRECT, dmRoom.getType());
        assertTrue(dmRoom.getName().contains("dm_"));
        assertEquals(2, dmRoom.getParticipants().size());

        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void testGetOrCreateDirectMessageRoom_ExistingRoom() {
        // Arrange
        Room existingDM = Room.builder()
                .id(UUID.randomUUID().toString())
                .name("dm_1_2")
                .type(Room.RoomType.DIRECT)
                .build();
        when(roomRepository.findDirectMessageRoom(1L, 2L)).thenReturn(Optional.of(existingDM));

        // Act
        Room dmRoom = roomService.getOrCreateDirectMessageRoom(1L, 2L);

        // Assert
        assertEquals(existingDM, dmRoom);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void testAddParticipant_Success() {
        // Arrange
        when(roomRepository.findById(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser1));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        roomService.addParticipant(testRoom.getId(), 1L);

        // Assert
        verify(roomRepository).save(testRoom);
        assertTrue(testRoom.getParticipants().contains(testUser1));
    }

    @Test
    void testAddParticipant_DirectRoomShouldFail() {
        // Arrange
        Room directRoom = Room.builder()
                .id(UUID.randomUUID().toString())
                .name("dm_1_2")
                .type(Room.RoomType.DIRECT)
                .build();
        when(roomRepository.findById(directRoom.getId())).thenReturn(Optional.of(directRoom));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser1));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            roomService.addParticipant(directRoom.getId(), 1L);
        });

        verify(roomRepository, never()).save(any());
    }

    @Test
    void testRemoveParticipant_Success() {
        // Arrange
        testRoom.getParticipants().add(testUser1);
        when(roomRepository.findById(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        roomService.removeParticipant(testRoom.getId(), 1L);

        // Assert
        verify(roomRepository).save(testRoom);
        assertFalse(testRoom.getParticipants().contains(testUser1));
    }

    @Test
    void testGetRoomById_Found() {
        // Arrange
        when(roomRepository.findById(testRoom.getId())).thenReturn(Optional.of(testRoom));

        // Act
        Optional<Room> result = roomService.getRoomById(testRoom.getId());

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testRoom, result.get());
    }

    @Test
    void testGetRoomByName_Found() {
        // Arrange
        when(roomRepository.findByName("test-room")).thenReturn(Optional.of(testRoom));

        // Act
        Optional<Room> result = roomService.getRoomByName("test-room");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testRoom, result.get());
    }

    @Test
    void testGetRoomsByType_Success() {
        // Arrange
        List<Room> publicRooms = Arrays.asList(testRoom);
        Page<Room> page = new PageImpl<>(publicRooms);
        when(roomRepository.findByType(eq(Room.RoomType.PUBLIC), any(PageRequest.class)))
                .thenReturn(page);

        // Act
        Page<Room> result = roomService.getRoomsByType(Room.RoomType.PUBLIC, 0, 10);

        // Assert
        assertEquals(1, result.getContent().size());
        assertEquals(testRoom, result.getContent().get(0));
    }

    @Test
    void testGetUserRooms_Success() {
        // Arrange
        List<Room> userRooms = Arrays.asList(testRoom);
        when(roomRepository.findByParticipant(1L)).thenReturn(userRooms);

        // Act
        List<Room> result = roomService.getUserRooms(1L);

        // Assert
        assertEquals(1, result.size());
        assertEquals(testRoom, result.get(0));
    }

    @Test
    void testIsParticipant_True() {
        // Arrange
        testRoom.getParticipants().add(testUser1);
        when(roomRepository.findById(testRoom.getId())).thenReturn(Optional.of(testRoom));

        // Act
        boolean result = roomService.isParticipant(testRoom.getId(), 1L);

        // Assert
        assertTrue(result);
    }

    @Test
    void testIsParticipant_False() {
        // Arrange
        when(roomRepository.findById(testRoom.getId())).thenReturn(Optional.of(testRoom));

        // Act
        boolean result = roomService.isParticipant(testRoom.getId(), 1L);

        // Assert
        assertFalse(result);
    }

    @Test
    void testDeleteRoom_Success() {
        // Arrange
        testRoom.setCreatorId(testUser1.getId());

        lenient().when(roomRepository.findById(testRoom.getId())).thenReturn(Optional.of(testRoom));

        // Make this stub lenient as well
        lenient().when(userRepository.findById(testUser1.getId()))
                .thenReturn(Optional.of(testUser1));

        // Act
        roomService.deleteRoom(testRoom.getId(), testUser1.getId());

        // Assert
        verify(roomRepository).deleteById(testRoom.getId());
    }
}
