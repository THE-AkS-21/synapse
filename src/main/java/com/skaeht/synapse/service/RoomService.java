package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing chat rooms and channels
 */
@Service
@Slf4j
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Create a new room
     */
    @Transactional
    public Room createRoom(String name, Room.RoomType type) {
        if (roomRepository.existsByName(name)) {
            throw new IllegalArgumentException("Room with name '" + name + "' already exists");
        }

        Room room = Room.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .type(type)
                .build();

        Room savedRoom = roomRepository.save(room);
        log.info("Created room: {} (type: {})", name, type);
        return savedRoom;
    }

    /**
     * Get or create a direct message room between two users
     */
    @Transactional
    public Room getOrCreateDirectMessageRoom(Long user1Id, Long user2Id) {
        // Check if room already exists
        Optional<Room> existingRoom = roomRepository.findDirectMessageRoom(user1Id, user2Id);
        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        // Also check reverse order
        existingRoom = roomRepository.findDirectMessageRoom(user2Id, user1Id);
        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        // Create new direct message room
        User user1 = userRepository.findById(user1Id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + user1Id));
        User user2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + user2Id));

        String roomName = "dm_" + Math.min(user1Id, user2Id) + "_" + Math.max(user1Id, user2Id);

        Room room = Room.builder()
                .id(UUID.randomUUID().toString())
                .name(roomName)
                .type(Room.RoomType.DIRECT)
                .build();

        room.getParticipants().add(user1);
        room.getParticipants().add(user2);

        Room savedRoom = roomRepository.save(room);
        log.info("Created direct message room between users {} and {}", user1Id, user2Id);
        return savedRoom;
    }

    /**
     * Add a user to a room
     */
    @Transactional
    @CacheEvict(value = "rooms", key = "#roomId")
    public void addParticipant(String roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (room.getType() == Room.RoomType.DIRECT) {
            throw new IllegalArgumentException("Cannot add participants to direct message rooms");
        }

        room.getParticipants().add(user);
        roomRepository.save(room);
        log.info("Added user {} to room {}", userId, roomId);
    }

    /**
     * Remove a user from a room
     */
    @Transactional
    @CacheEvict(value = "rooms", key = "#roomId")
    public void removeParticipant(String roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        room.getParticipants().removeIf(user -> user.getId().equals(userId));
        roomRepository.save(room);
        log.info("Removed user {} from room {}", userId, roomId);
    }

    /**
     * Get a room by ID
     */
    @Cacheable(value = "rooms", key = "#roomId")
    public Optional<Room> getRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    /**
     * Get a room by name
     */
    public Optional<Room> getRoomByName(String name) {
        return roomRepository.findByName(name);
    }

    /**
     * Get all rooms by type with pagination
     */
    public Page<Room> getRoomsByType(Room.RoomType type, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return roomRepository.findByType(type, pageable);
    }

    /**
     * Get all rooms a user is a participant in
     */
    public List<Room> getUserRooms(Long userId) {
        return roomRepository.findByParticipant(userId);
    }

    /**
     * Check if a user is a participant in a room
     */
    public boolean isParticipant(String roomId, Long userId) {
        Optional<Room> room = roomRepository.findById(roomId);
        if (room.isEmpty()) {
            return false;
        }

        return room.get().getParticipants().stream()
                .anyMatch(user -> user.getId().equals(userId));
    }

    /**
     * Get all public rooms
     */
    public Page<Room> getPublicRooms(int page, int size) {
        return getRoomsByType(Room.RoomType.PUBLIC, page, size);
    }

    /**
     * Delete a room
     */
    @Transactional
    @CacheEvict(value = "rooms", key = "#roomId")
    public void deleteRoom(String roomId) {
        roomRepository.deleteById(roomId);
        log.info("Deleted room {}", roomId);
    }
}
