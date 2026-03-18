package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.exception.ResourceNotFoundException;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a unique 12-digit numeric room ID formatted as XXXX-YYYY-ZZZZ.
     */
    private String generateNumericRoomId() {
        // Generates a 12-digit numeric string split by hyphens: XXXX-YYYY-ZZZZ
        return String.format("%04d-%04d-%04d",
                secureRandom.nextInt(10000),
                secureRandom.nextInt(10000),
                secureRandom.nextInt(10000));
    }

    @Transactional
    public Room createRoom(String name, Room.RoomType type, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));

        Room room = Room.builder()
                .id(generateNumericRoomId()) // Adjusted for standard Lombok syntax
                .name(name)
                .type(type)
                .creator(creator)
                // If you use @Builder.Default on the Set in your entity, you don't need the initialization below,
                // but it's safest to define it explicitly if not.
                .build();

        // Safely initialize the collection if it's null before adding the creator
        if (room.getParticipants() == null) {
            room.setParticipants(new HashSet<>());
        }

        room.getParticipants().add(creator);

        // Optional: If you maintain bidirectional relationships in JPA,
        // you might also need: creator.getRooms().add(room);

        return roomRepository.save(room);
    }

    @Transactional
    public Room getOrCreateDirectMessage(String currentUserEmail, String targetDisplayId) {
        User current = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        User target = userRepository.findByDisplayId(targetDisplayId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        // 1. Check if a DM room already exists between these two exact users
        // (You will need a custom @Query in RoomRepository if this method doesn't exist)
        Optional<Room> existingDM = roomRepository.findDirectMessageRoom(current.getId(), target.getId());

        if (existingDM.isPresent()) {
            return existingDM.get();
        }

        // 2. Create new DM Room
        Room dmRoom = Room.builder()
                .id(generateNumericRoomId())
                .name(current.getDisplayId() + "_" + target.getDisplayId())
                .type(Room.RoomType.DIRECT)
                .build();

        if (dmRoom.getParticipants() == null) {
            dmRoom.setParticipants(new HashSet<>());
        }

        dmRoom.getParticipants().add(current);
        dmRoom.getParticipants().add(target);

        return roomRepository.save(dmRoom);
    }

    public Optional<Room> getRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    public Room getRoom(String roomId) {
        return getRoomById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
    }

    public List<Room> getPublicRooms() {
        return roomRepository.findByType(Room.RoomType.PUBLIC);
    }

    public List<Room> getUserRooms(Long userId) {
        return roomRepository.findByParticipants_Id(userId);
    }

    public List<Room> getRoomsForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return getUserRooms(user.getId());
    }

    @Transactional(readOnly = true)
    public List<User> getRoomParticipants(String roomId) {
        Room room = getRoom(roomId);
        return new ArrayList<>(room.getParticipants());
    }

    @Transactional
    public void addParticipant(String roomId, Long userId) {
        Room room = getRoom(roomId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        room.getParticipants().add(user);
        roomRepository.save(room);
    }

    @Transactional
    public void removeMember(String roomId, Long userId, String requesterEmail) {
        Room room = getRoom(roomId);
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        User userToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        if (!room.getCreator().getId().equals(requester.getId()) && !requester.getId().equals(userId)) {
            throw new SecurityException("Not authorized to remove member");
        }
        room.getParticipants().remove(userToRemove);
        roomRepository.save(room);
    }

    @Transactional
    public void deleteRoom(String roomId, Long requesterId) {
        // 1. Find the requester
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));

        // 2. Find the room using the built-in findById
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        // 3. Authorization Check
        if (!room.getCreator().getId().equals(requester.getId())) {
            throw new AccessDeniedException("You do not have permission to delete this room.");
        }

        // 4. Perform deletion
        roomRepository.delete(room);
    }

    @Transactional
    public Room updateTheme(String roomId, String theme, Long requesterId) {
        Room room = getRoom(roomId);
        if (!room.getCreator().getId().equals(requesterId)) {
            throw new SecurityException("Only creator can update theme");
        }
        room.setTheme(theme);
        return roomRepository.save(room);
    }

    @Transactional
    public void clearMessages(String roomId, Long requesterId) {
        Room room = getRoom(roomId);
        if (!room.getCreator().getId().equals(requesterId)) {
            throw new SecurityException("Only creator can clear messages");
        }
        messageRepository.deleteByRoomId(roomId);
    }
}