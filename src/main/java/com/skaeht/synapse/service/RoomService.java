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
import org.springframework.security.access.AccessDeniedException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private String generateNumericRoomId() {
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
                .id(generateNumericRoomId())
                .name(name)
                .type(type)
                .creator(creator)
                .build();

        if (room.getParticipants() == null) {
            room.setParticipants(new HashSet<>());
        }

        room.getParticipants().add(creator);
        return roomRepository.save(room);
    }

    @Transactional
    public Room getOrCreateDirectMessage(String currentUserEmail, String targetDisplayId) {
        User current = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        User target = userRepository.findByDisplayId(targetDisplayId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        Optional<Room> existingDM = roomRepository.findDirectMessageRoom(current.getId(), target.getId());

        if (existingDM.isPresent()) {
            Room room = existingDM.get();
            room.getParticipants().size(); // Initialize lazy collection
            return room;
        }

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

    @Transactional(readOnly = true)
    public Optional<Room> getRoomById(String roomId) {
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        // CRITICAL FIX: Initialize the participants collection while transaction is open
        roomOpt.ifPresent(room -> room.getParticipants().size());
        return roomOpt;
    }

    @Transactional(readOnly = true)
    public Room getRoom(String roomId) {
        return getRoomById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
    }

    @Transactional(readOnly = true)
    public List<Room> getPublicRooms() {
        List<Room> rooms = roomRepository.findByType(Room.RoomType.PUBLIC);
        // CRITICAL FIX: Initialize lazy collections
        rooms.forEach(room -> room.getParticipants().size());
        return rooms;
    }

    @Transactional(readOnly = true)
    public List<Room> getUserRooms(Long userId) {
        List<Room> rooms = roomRepository.findByParticipants_Id(userId);
        // CRITICAL FIX: Initialize lazy collections
        rooms.forEach(room -> room.getParticipants().size());
        return rooms;
    }

    @Transactional(readOnly = true)
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
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        if (!room.getCreator().getId().equals(requester.getId())) {
            throw new AccessDeniedException("You do not have permission to delete this room.");
        }

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

    @Transactional
    public Room getOrCreateDirectMessageByIds(Long user1Id, Long user2Id) {
        Optional<Room> existingDM = roomRepository.findDirectMessageRoom(user1Id, user2Id);
        if (existingDM.isPresent()) {
            Room room = existingDM.get();
            room.getParticipants().size(); // Initialize lazy collection
            return room;
        }

        User user1 = userRepository.findById(user1Id).orElseThrow(() -> new ResourceNotFoundException("User 1 not found"));
        User user2 = userRepository.findById(user2Id).orElseThrow(() -> new ResourceNotFoundException("User 2 not found"));

        Room dmRoom = Room.builder()
                .id(generateNumericRoomId())
                .name(user1.getUsername() + "_" + user2.getUsername())
                .type(Room.RoomType.DIRECT)
                .build();

        if (dmRoom.getParticipants() == null) {
            dmRoom.setParticipants(new HashSet<>());
        }

        dmRoom.getParticipants().add(user1);
        dmRoom.getParticipants().add(user2);

        return roomRepository.save(dmRoom);
    }
}