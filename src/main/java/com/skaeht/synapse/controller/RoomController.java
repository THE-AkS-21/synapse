package com.skaeht.synapse.controller;

import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

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

    /**
     * Create a new room
     */
    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody Map<String, String> request, Authentication authentication) {
        String name = request.get("name");
        String typeStr = request.getOrDefault("type", "PUBLIC");

        Room.RoomType type = Room.RoomType.valueOf(typeStr.toUpperCase());
        Room room = roomService.createRoom(name, type, authentication.getName());

        return ResponseEntity.ok(room);
    }

    /**
     * Get room by ID
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable String roomId) {
        return roomService.getRoomById(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all public rooms
     */
    @GetMapping("/public")
    public ResponseEntity<Page<Room>> getPublicRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Room> rooms = roomService.getPublicRooms(page, size);
        return ResponseEntity.ok(rooms);
    }

    /**
     * Get rooms for a specific user
     */
    @GetMapping("/user")
    public ResponseEntity<List<Room>> getUserRooms(Authentication authentication) {

        String username = authentication.getName();

        List<Room> rooms = roomService.getRoomsForUser(username);

        return ResponseEntity.ok(rooms);
    }

    /**
     * Add a participant to a room
     */
    @PostMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> addParticipant(
            @PathVariable String roomId,
            @PathVariable Long userId) {
        roomService.addParticipant(roomId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Remove a participant from a room
     */
    @DeleteMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable String roomId,
            @PathVariable Long userId) {
        roomService.removeParticipant(roomId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Create or get direct message room between two users
     */
    @PostMapping("/direct")
    public ResponseEntity<Room> getOrCreateDirectRoom(@RequestBody Map<String, Long> request) {
        Long user1Id = request.get("user1Id");
        Long user2Id = request.get("user2Id");

        Room room = roomService.getOrCreateDirectMessageRoom(user1Id, user2Id);
        return ResponseEntity.ok(room);
    }

    /**
     * Delete a room
     */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId) {
        roomService.deleteRoom(roomId);
        return ResponseEntity.ok().build();
    }
}
