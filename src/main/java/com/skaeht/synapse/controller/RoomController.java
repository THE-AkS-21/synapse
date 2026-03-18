package com.skaeht.synapse.controller;

import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.security.UserDetailsImpl;
import com.skaeht.synapse.service.RoomService;
import com.skaeht.synapse.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.skaeht.synapse.dto.response.UserProfileResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rooms")
@Slf4j
public class RoomController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    /**
     * Helper method to map Room entities to safe Maps.
     */
    private Map<String, Object> mapRoomToResponse(Room room) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", room.getId());
        map.put("name", room.getName());
        map.put("type", room.getType().name());
        map.put("creatorId", room.getCreator() != null ? room.getCreator().getId() : null);
        map.put("theme", room.getTheme());

        // CRITICAL FIX: Ensure participants are included safely so the frontend
        // can dynamically figure out the other user's name in Direct Messages.
        if (room.getParticipants() != null) {
            map.put("participants", room.getParticipants().stream()
                    .map(p -> Map.of("id", p.getId(), "username", p.getUsername()))
                    .collect(Collectors.toList()));
        }

        return map;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRoom(@RequestBody Map<String, String> request, Authentication authentication) {
        String name = request.get("name");
        String typeStr = request.getOrDefault("type", "PUBLIC");
        Room.RoomType type = Room.RoomType.valueOf(typeStr.toUpperCase());

        var userOpt = userService.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        Room room = roomService.createRoom(name, type, userOpt.get().getId());
        return ResponseEntity.ok(mapRoomToResponse(room));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<Map<String, Object>> getRoom(@PathVariable String roomId) {
        return roomService.getRoomById(roomId)
                .map(room -> ResponseEntity.ok(mapRoomToResponse(room)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/public")
    public ResponseEntity<List<Map<String, Object>>> getPublicRooms() {
        List<Map<String, Object>> safeRooms = roomService.getPublicRooms()
                .stream()
                .map(this::mapRoomToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(safeRooms);
    }

    @GetMapping("/user")
    public ResponseEntity<List<Map<String, Object>>> getUserRooms(Authentication authentication) {
        var userOpt = userService.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        List<Map<String, Object>> enrichedRooms = roomService.getUserRooms(userOpt.get().getId())
                .stream()
                .map(this::mapRoomToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(enrichedRooms);
    }

    @DeleteMapping("/{roomId}/messages")
    public ResponseEntity<Void> clearRoomMessages(@PathVariable String roomId, Authentication authentication) {
        var userOpt = userService.findByEmail(authentication.getName());
        if (userOpt.isPresent()) {
            roomService.clearMessages(roomId, userOpt.get().getId());
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(401).build();
    }

    @PostMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> addParticipant(@PathVariable String roomId, @PathVariable Long userId) {
        roomService.addParticipant(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable String roomId,
            @PathVariable Long userId,
            Authentication authentication) {
        var requesterOpt = userService.findByEmail(authentication.getName());
        if (requesterOpt.isEmpty()) return ResponseEntity.status(401).build();

        roomService.removeMember(roomId, userId, requesterOpt.get().getUsername());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        roomService.deleteRoom(roomId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, Authentication authentication) {
        var roomOpt = roomService.getRoomById(roomId);
        if (roomOpt.isEmpty()) return ResponseEntity.notFound().build();

        if (roomOpt.get().getType() == Room.RoomType.PRIVATE) {
            return ResponseEntity.status(403).body(Map.of("message", "This room is private."));
        }

        var userOpt = userService.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        roomService.addParticipant(roomId, userOpt.get().getId());
        return ResponseEntity.ok(mapRoomToResponse(roomOpt.get()));
    }

    @PatchMapping("/{roomId}/theme")
    public ResponseEntity<Map<String, Object>> updateTheme(
            @PathVariable String roomId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        var userOpt = userService.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        Room updated = roomService.updateTheme(roomId, request.get("theme"), userOpt.get().getId());
        return ResponseEntity.ok(mapRoomToResponse(updated));
    }

    @GetMapping("/{roomId}/participants")
    public ResponseEntity<List<UserProfileResponse>> getRoomParticipants(@PathVariable String roomId) {
        List<com.skaeht.synapse.entity.User> rawUsers = roomService.getRoomParticipants(roomId);
        List<UserProfileResponse> safeParticipants = rawUsers.stream()
                .map(user -> new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(safeParticipants);
    }

    @PostMapping("/direct")
    public ResponseEntity<Map<String, Object>> startDirectMessage(@RequestBody Map<String, Long> request, Authentication authentication) {
        Long user1Id = request.get("user1Id");
        Long user2Id = request.get("user2Id");

        var userOpt = userService.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        if (!userOpt.get().getId().equals(user1Id) && !userOpt.get().getId().equals(user2Id)) {
            return ResponseEntity.status(403).build();
        }

        Room dmRoom = roomService.getOrCreateDirectMessageByIds(user1Id, user2Id);
        return ResponseEntity.ok(mapRoomToResponse(dmRoom));
    }
}