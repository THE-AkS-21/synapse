package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.exception.ResourceNotFoundException;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final RedisPublisher redisPublisher;
    private final SimpMessagingTemplate messagingTemplate; // INJECTED for global WS broadcasts
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
        Room savedRoom = roomRepository.save(room);

        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "ROOM_CREATED",
                        "roomId", savedRoom.getId(),
                        "roomType", type.name(),
                        "participantIds", List.of(creator.getId())
                ));

        return savedRoom;
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
            room.getParticipants().size();
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

        Room savedRoom = roomRepository.save(dmRoom);

        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "ROOM_CREATED",
                        "roomId", savedRoom.getId(),
                        "participantIds", List.of(current.getId(), target.getId())
                ));

        return savedRoom;
    }

    @Transactional(readOnly = true)
    public Optional<Room> getRoomById(String roomId) {
        Optional<Room> roomOpt = roomRepository.findById(roomId);
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
        rooms.forEach(room -> room.getParticipants().size());
        return rooms;
    }

    @Transactional(readOnly = true)
    public List<Room> getUserRooms(Long userId) {
        List<Room> rooms = roomRepository.findByParticipants_Id(userId);
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

        // Broadcast a system message to the room that the user joined
        ChatMessage joinedEvent = new ChatMessage(
                roomId, 0L, "SYSTEM", "USER_JOINED:" + userId, System.currentTimeMillis()
        );
        redisPublisher.publish(joinedEvent);

        // Notify global users so the added user spontaneously fetches the room
        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "USER_ADDED_TO_ROOM", "roomId", roomId, "userId", userId));
    }

    @Transactional
    public void removeMember(String roomId, Long userId, String requesterEmail) {
        Room room = getRoom(roomId);
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        User userToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        if (room.getType() == Room.RoomType.DIRECT) {
            throw new SecurityException("Cannot remove members from a direct message chat");
        } else {
            boolean isCreator = room.getCreator() != null && room.getCreator().getId().equals(requester.getId());
            boolean isSelf = requester.getId().equals(userId);

            if (!isCreator && !isSelf) {
                throw new SecurityException("Not authorized to remove member");
            }
        }

        room.getParticipants().remove(userToRemove);
        roomRepository.save(room);

        // Broadcast a system message that the user was removed
        ChatMessage removalEvent = new ChatMessage(
                roomId, 0L, "SYSTEM", "USER_REMOVED:" + userId, System.currentTimeMillis()
        );
        redisPublisher.publish(removalEvent);

        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "USER_REMOVED_FROM_ROOM", "roomId", roomId, "targetId", userId));
    }

    @Transactional
    public void deleteRoom(String roomId, Long requesterId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        if (room.getType() == Room.RoomType.DIRECT) {
            boolean isParticipant = room.getParticipants().stream()
                    .anyMatch(p -> p.getId().equals(requesterId));
            if (!isParticipant) {
                throw new AccessDeniedException("You do not have permission to delete this DM.");
            }
        } else {
            if (room.getCreator() == null || !room.getCreator().getId().equals(requesterId)) {
                throw new AccessDeniedException("You do not have permission to delete this room.");
            }
        }

        // Send a direct chat signal to kick existing users before deleting
        ChatMessage deleteEvent = new ChatMessage(
                roomId, 0L, "SYSTEM", "ROOM_DELETED", System.currentTimeMillis()
        );
        redisPublisher.publish(deleteEvent);

        roomRepository.delete(room);

        // Dispatch globally to strip it dynamically from sidebar views
        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "ROOM_DELETED", "roomId", roomId));
    }

    @Transactional
    public Room updateTheme(String roomId, String theme, Long requesterId) {
        Room room = getRoom(roomId);
        // ... [Remains identical]
        if (room.getType() == Room.RoomType.DIRECT) {
            boolean isParticipant = room.getParticipants().stream()
                    .anyMatch(p -> p.getId().equals(requesterId));
            if (!isParticipant) {
                throw new SecurityException("Only participants can update DM theme");
            }
        } else {
            if (room.getCreator() == null || !room.getCreator().getId().equals(requesterId)) {
                throw new SecurityException("Only creator can update theme");
            }
        }
        room.setTheme(theme);
        return roomRepository.save(room);
    }

    @Transactional
    public void clearMessages(String roomId, Long requesterId) {
        // ... [Remains Identical]
        Room room = getRoom(roomId);

        if (room.getType() == Room.RoomType.DIRECT) {
            boolean isParticipant = room.getParticipants().stream()
                    .anyMatch(p -> p.getId().equals(requesterId));
            if (!isParticipant) {
                throw new SecurityException("Only participants can clear DM messages");
            }
        } else {
            if (room.getCreator() == null || !room.getCreator().getId().equals(requesterId)) {
                throw new SecurityException("Only the creator can clear messages");
            }
        }

        messageRepository.deleteByRoomId(roomId);
    }

    @Transactional
    public Room getOrCreateDirectMessageByIds(Long user1Id, Long user2Id) {
        Optional<Room> existingDM = roomRepository.findDirectMessageRoom(user1Id, user2Id);
        if (existingDM.isPresent()) {
            Room room = existingDM.get();
            room.getParticipants().size();
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

        Room savedRoom = roomRepository.save(dmRoom);

        // Ensure both users see the newly created DM immediately
        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "ROOM_CREATED",
                        "roomId", savedRoom.getId(),
                        "participantIds", List.of(user1.getId(), user2.getId())
                ));

        return savedRoom;
    }
}