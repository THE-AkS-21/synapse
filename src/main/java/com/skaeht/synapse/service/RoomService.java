package com.skaeht.synapse.service;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.exception.ResourceNotFoundException;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.util.IdGeneratorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Room createRoom(String name, Room.RoomType type, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));

        Room room = Room.builder()
                .id(IdGeneratorUtil.generateNumericRoomId())
                .name(name)
                .type(type)
                .creator(creator)
                .participants(new HashSet<>())
                .build();

        room.getParticipants().add(creator);
        Room savedRoom = roomRepository.save(room);

        broadcastGlobalEvent("ROOM_CREATED", savedRoom.getId(), Map.of(
                "roomType", type.name(),
                "participantIds", List.of(creator.getId())
        ));

        return savedRoom;
    }

    @Transactional(readOnly = true)
    public Optional<Room> getRoomById(String roomId) {
        return roomRepository.findById(roomId).map(room -> {
            room.getParticipants().size(); // HIBERNATE HACK: Force initialize lazy collection before TX closes
            return room;
        });
    }

    @Transactional(readOnly = true)
    public Room getRoom(String roomId) {
        return getRoomById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
    }

    @Transactional(readOnly = true)
    public List<Room> getPublicRooms() {
        List<Room> rooms = roomRepository.findByType(Room.RoomType.PUBLIC);
        rooms.forEach(room -> room.getParticipants().size()); // Initialize lazy collections
        return rooms;
    }

    @Transactional(readOnly = true)
    public List<Room> getUserRooms(Long userId) {
        List<Room> rooms = roomRepository.findByParticipants_Id(userId);
        rooms.forEach(room -> room.getParticipants().size()); // Initialize lazy collections
        return rooms;
    }

    @Transactional(readOnly = true)
    public List<User> getRoomParticipants(String roomId) {
        return new ArrayList<>(getRoom(roomId).getParticipants());
    }

    @Transactional
    public void addParticipant(String roomId, Long userId) {
        Room room = getRoom(roomId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        room.getParticipants().add(user);
        roomRepository.save(room);

        redisPublisher.publish(new ChatMessage(roomId, 0L, "SYSTEM", "USER_JOINED:" + userId, System.currentTimeMillis()));
        broadcastGlobalEvent("USER_ADDED_TO_ROOM", roomId, Map.of("userId", userId));
    }

    @Transactional
    public void removeMember(String roomId, Long userId, String requesterEmail) {
        Room room = getRoom(roomId);
        if (room.getType() == Room.RoomType.DIRECT) {
            throw new SecurityException("Cannot remove members from a direct message chat");
        }

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));

        boolean isCreator = room.getCreator() != null && room.getCreator().getId().equals(requester.getId());
        boolean isSelf = requester.getId().equals(userId);

        if (!isCreator && !isSelf) {
            throw new SecurityException("Not authorized to remove member");
        }

        room.getParticipants().removeIf(p -> p.getId().equals(userId));
        roomRepository.save(room);

        redisPublisher.publish(new ChatMessage(roomId, 0L, "SYSTEM", "USER_REMOVED:" + userId, System.currentTimeMillis()));
        broadcastGlobalEvent("USER_REMOVED_FROM_ROOM", roomId, Map.of("targetId", userId));
    }

    @Transactional
    public void deleteRoom(String roomId, Long requesterId) {
        Room room = getRoom(roomId);
        verifyRoomActionPermission(room, requesterId, "delete");

        redisPublisher.publish(new ChatMessage(roomId, 0L, "SYSTEM", "ROOM_DELETED", System.currentTimeMillis()));
        roomRepository.delete(room);
        broadcastGlobalEvent("ROOM_DELETED", roomId, Map.of());
    }

    @Transactional
    public Room updateTheme(String roomId, String theme, Long requesterId) {
        Room room = getRoom(roomId);
        verifyRoomActionPermission(room, requesterId, "update theme for");

        room.setTheme(theme);
        return roomRepository.save(room);
    }

    @Transactional
    public void clearMessages(String roomId, Long requesterId) {
        Room room = getRoom(roomId);
        verifyRoomActionPermission(room, requesterId, "clear messages in");
        messageRepository.deleteByRoomId(roomId);

        ChatMessage systemEvent = new ChatMessage(
                roomId,
                0L,
                "SYSTEM",
                "MESSAGES_CLEARED",
                System.currentTimeMillis()
        );
        redisPublisher.publish(systemEvent);
    }

    @Transactional
    public Room getOrCreateDirectMessageByIds(Long user1Id, Long user2Id) {
        return roomRepository.findDirectMessageRoom(user1Id, user2Id).map(room -> {
            room.getParticipants().size(); // Force lazy init
            return room;
        }).orElseGet(() -> {
            User user1 = userRepository.findById(user1Id).orElseThrow();
            User user2 = userRepository.findById(user2Id).orElseThrow();

            Room dmRoom = Room.builder()
                    .id(IdGeneratorUtil.generateNumericRoomId())
                    .name(user1.getUsername() + "_" + user2.getUsername())
                    .type(Room.RoomType.DIRECT)
                    .participants(new HashSet<>(List.of(user1, user2)))
                    .build();

            Room savedRoom = roomRepository.save(dmRoom);

            broadcastGlobalEvent("ROOM_CREATED", savedRoom.getId(), Map.of(
                    "participantIds", List.of(user1.getId(), user2.getId())
            ));

            return savedRoom;
        });
    }

    // --- DRY HELPER METHODS --- //

    /** Centralized permission check to DRY up controller actions */
    private void verifyRoomActionPermission(Room room, Long requesterId, String action) {
        if (room.getType() == Room.RoomType.DIRECT) {
            boolean isParticipant = room.getParticipants().stream()
                    .anyMatch(p -> p.getId().equals(requesterId));
            if (!isParticipant) {
                throw new AccessDeniedException("Only participants can " + action + " this DM.");
            }
        } else {
            if (room.getCreator() == null || !room.getCreator().getId().equals(requesterId)) {
                throw new AccessDeniedException("Only the room creator can " + action + " this room.");
            }
        }
    }

    /** Helper to standardize WebSocket payload emission */
    private void broadcastGlobalEvent(String type, String roomId, Map<String, Object> additionalData) {
        Map<String, Object> payload = new java.util.HashMap<>(additionalData);
        payload.put("type", type);
        payload.put("roomId", roomId);
        messagingTemplate.convertAndSend("/topic/global-events", payload);
    }
}