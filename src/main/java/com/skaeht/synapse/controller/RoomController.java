package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.response.RoomResponse;
import com.skaeht.synapse.dto.response.UserProfileResponse;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.exception.ResourceNotFoundException;
import com.skaeht.synapse.security.UserDetailsImpl;
import com.skaeht.synapse.service.RoomService;
import com.skaeht.synapse.service.UserService;
import com.skaeht.synapse.util.RoomMapperUtil;
import com.skaeht.synapse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
@Slf4j
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@RequestBody Map<String, String> request,
                                                   Authentication authentication) {
        String name = request.get("name");
        Room.RoomType type = Room.RoomType.valueOf(request.getOrDefault("type", "PUBLIC").toUpperCase());

        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Room room = roomService.createRoom(name, type, user.getId());
        return ResponseEntity.ok(RoomMapperUtil.toRoomResponse(room));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String roomId) {
        return roomService.getRoomById(roomId)
                .map(room -> ResponseEntity.ok(RoomMapperUtil.toRoomResponse(room)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/public")
    public ResponseEntity<List<RoomResponse>> getPublicRooms() {
        List<RoomResponse> safeRooms = roomService.getPublicRooms().stream()
                .map(RoomMapperUtil::toRoomResponse)
                .toList();
        return ResponseEntity.ok(safeRooms);
    }

    @GetMapping("/user")
    public ResponseEntity<List<RoomResponse>> getUserRooms(Authentication authentication) {
        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<RoomResponse> enrichedRooms = roomService.getUserRooms(user.getId()).stream()
                .map(RoomMapperUtil::toRoomResponse)
                .toList();

        return ResponseEntity.ok(enrichedRooms);
    }

    @DeleteMapping("/{roomId}/messages")
    public ResponseEntity<Void> clearRoomMessages(@PathVariable String roomId, Authentication authentication) {
        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        roomService.clearMessages(roomId, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> addParticipant(@PathVariable String roomId, @PathVariable Long userId) {
        roomService.addParticipant(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(@PathVariable String roomId,
                                                  @PathVariable Long userId,
                                                  Authentication authentication) {
        String userEmail = SecurityUtil.getCurrentUserEmail(authentication);
        roomService.removeMember(roomId, userId, userEmail);
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
        Room room = roomService.getRoomById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        if (room.getType() == Room.RoomType.PRIVATE) {
            return ResponseEntity.status(403).body(Map.of("message", "This room is private."));
        }

        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        roomService.addParticipant(roomId, user.getId());
        return ResponseEntity.ok(RoomMapperUtil.toRoomResponse(room));
    }

    @PatchMapping("/{roomId}/theme")
    public ResponseEntity<RoomResponse> updateTheme(@PathVariable String roomId,
                                                    @RequestBody Map<String, String> request,
                                                    Authentication authentication) {
        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Room updated = roomService.updateTheme(roomId, request.get("theme"), user.getId());
        return ResponseEntity.ok(RoomMapperUtil.toRoomResponse(updated));
    }

    @GetMapping("/{roomId}/participants")
    public ResponseEntity<List<UserProfileResponse>> getRoomParticipants(@PathVariable String roomId) {
        List<UserProfileResponse> safeParticipants = roomService.getRoomParticipants(roomId).stream()
                .map(user -> new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayId()))
                .toList();

        return ResponseEntity.ok(safeParticipants);
    }

    @PostMapping("/direct")
    public ResponseEntity<RoomResponse> startDirectMessage(@RequestBody Map<String, Long> request,
                                                           Authentication authentication) {
        Long user1Id = request.get("user1Id");
        Long user2Id = request.get("user2Id");

        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getId().equals(user1Id) && !user.getId().equals(user2Id)) {
            return ResponseEntity.status(403).build();
        }

        Room dmRoom = roomService.getOrCreateDirectMessageByIds(user1Id, user2Id);
        return ResponseEntity.ok(RoomMapperUtil.toRoomResponse(dmRoom));
    }
}