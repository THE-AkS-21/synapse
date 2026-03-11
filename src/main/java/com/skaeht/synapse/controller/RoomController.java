package com.skaeht.synapse.controller;

import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.skaeht.synapse.dto.UserProfileResponse;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;

/**
 * REST controller for room management
 */
@RestController
@RequestMapping("/api/v1/rooms")
@Slf4j
public class RoomController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private com.skaeht.synapse.service.UserService userService;

    /** Create a new room */
    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody Map<String, String> request, Authentication authentication) {
        String name = request.get("name");
        String typeStr = request.getOrDefault("type", "PUBLIC");
        Room.RoomType type = Room.RoomType.valueOf(typeStr.toUpperCase());

        // Fetch the user to get their ID
        var userOpt = userService.findByUsername(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        // Pass the user ID instead of the username
        Room room = roomService.createRoom(name, type, userOpt.get().getId());
        return ResponseEntity.ok(room);
    }

    /** Get room by ID */
    @GetMapping("/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable String roomId) {
        return roomService.getRoomById(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Get all public rooms */
    @GetMapping("/public")
    public ResponseEntity<Page<Room>> getPublicRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(roomService.getPublicRooms(page, size));
    }

    /** Get rooms for the authenticated user */
    @GetMapping("/user")
    public ResponseEntity<List<Map<String, Object>>> getUserRooms(Authentication authentication) {
        var userOpt = userService.findByUsername(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();
        com.skaeht.synapse.entity.User currentUser = userOpt.get();

        List<Room> rooms = roomService.getRoomsForUser(currentUser.getUsername());

        // Map Room entities to Maps so we can inject the DM Partner's details
        List<Map<String, Object>> enrichedRooms = rooms.stream().map(room -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", room.getId());
            map.put("name", room.getName());
            map.put("type", room.getType().name());
            map.put("creatorId", room.getCreatorId());
            map.put("theme", room.getTheme());

            // If it's a DM, find the OTHER user and attach their info
            if (room.getType() == Room.RoomType.DIRECT) {
                room.getParticipants().stream()
                        .filter(p -> !p.getId().equals(currentUser.getId()))
                        .findFirst()
                        .ifPresent(partner -> {
                            map.put("dmPartner", partner.getUsername());
                            map.put("dmPartnerDisplayId", partner.getDisplayId());
                        });
            }
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(enrichedRooms);
    }

    // 2. Add this NEW endpoint for clearing messages
    @DeleteMapping("/{roomId}/messages")
    public ResponseEntity<Void> clearRoomMessages(@PathVariable String roomId, Authentication authentication) {
        var userOpt = userService.findByUsername(authentication.getName());
        if (userOpt.isPresent()) {
            roomService.clearMessages(roomId, userOpt.get().getId());
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(401).build();
    }

    /** Add a participant to a room */
    @PostMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> addParticipant(@PathVariable String roomId, @PathVariable Long userId) {
        roomService.addParticipant(roomId, userId);
        return ResponseEntity.ok().build();
    }

    /** Remove a participant from a room (creator only) */
    @DeleteMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable String roomId,
            @PathVariable Long userId,
            Authentication authentication) {
        roomService.removeMember(roomId, userId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    /** Create or get direct message room */
    @PostMapping("/direct")
    public ResponseEntity<Room> getOrCreateDirectRoom(@RequestBody Map<String, Long> request) {
        Long user1Id = request.get("user1Id");
        Long user2Id = request.get("user2Id");
        return ResponseEntity.ok(roomService.getOrCreateDirectMessageRoom(user1Id, user2Id));
    }

    /** Delete a room (creator only) */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId, Authentication authentication) {
        roomService.deleteRoom(roomId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    /** Join a public room (authenticated user adds themselves) */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, Authentication authentication) {
        var roomOpt = roomService.getRoomById(roomId);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var room = roomOpt.get();
        if (room.getType() == Room.RoomType.PRIVATE) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("message", "This room is private. You need an invitation to join."));
        }
        // Get current user id from UserService via authentication name
        var userOpt = userService.findByUsername(authentication.getName());
        if (userOpt.isEmpty())
            return ResponseEntity.status(401).build();
        roomService.addParticipant(roomId, userOpt.get().getId());
        return ResponseEntity.ok(room);
    }

    /** Update room theme (creator only) */
    @PatchMapping("/{roomId}/theme")
    public ResponseEntity<Room> updateTheme(
            @PathVariable String roomId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String theme = request.get("theme");

        // Fetch the user to get their ID
        var userOpt = userService.findByUsername(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        // Pass the user ID instead of the username
        Room updated = roomService.updateTheme(roomId, theme, userOpt.get().getId());
        return ResponseEntity.ok(updated);
    }

    // Endpoint to fetch ALL room participants (online + offline) safely
    @GetMapping("/{roomId}/participants")
    public ResponseEntity<List<UserProfileResponse>> getRoomParticipants(@PathVariable String roomId) {

        List<com.skaeht.synapse.entity.User> rawUsers = roomService.getRoomParticipants(roomId);

        // Map raw User entities to a safe DTO to prevent leaking passwords
        List<UserProfileResponse> safeParticipants = rawUsers.stream()
                .map(user -> new UserProfileResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getDisplayId()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(safeParticipants);
    }
}