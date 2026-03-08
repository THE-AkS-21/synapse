package com.skaeht.synapse.controller;

import com.skaeht.synapse.entity.Invitation;
import com.skaeht.synapse.service.InvitationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for invitation management.
 * All endpoints require authentication (handled by SecurityConfig for
 * /api/v1/**).
 */
@RestController
@RequestMapping("/api/v1/invitations")
@Slf4j
public class InvitationController {

    @Autowired
    private InvitationService invitationService;

    /**
     * Send a room invitation.
     * Body: { "roomId": "...", "toDisplayId": "XXXX-XXXX-XXXX" }
     * Body: { "type": "DM", "toDisplayId": "XXXX-XXXX-XXXX" }
     */
    @PostMapping
    public ResponseEntity<?> sendInvitation(@RequestBody Map<String, String> request, Authentication auth) {
        String type = request.getOrDefault("type", "ROOM");
        String toDisplayId = request.get("toDisplayId");

        if (toDisplayId == null || toDisplayId.isBlank()) {
            return ResponseEntity.badRequest().body("toDisplayId is required");
        }

        try {
            if ("DM".equalsIgnoreCase(type)) {
                Invitation inv = invitationService.sendDMInvitation(auth.getName(), toDisplayId);
                return ResponseEntity.ok(inv);
            } else {
                String roomId = request.get("roomId");
                if (roomId == null || roomId.isBlank()) {
                    return ResponseEntity.badRequest().body("roomId is required for ROOM invitations");
                }
                Invitation inv = invitationService.sendRoomInvitation(roomId, auth.getName(), toDisplayId);
                return ResponseEntity.ok(inv);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    /** Get all pending invitations for the current user. */
    @GetMapping("/pending")
    public ResponseEntity<List<Invitation>> getPendingInvitations(Authentication auth) {
        List<Invitation> invitations = invitationService.getPendingInvitations(auth.getName());
        return ResponseEntity.ok(invitations);
    }

    /** Accept an invitation. */
    @PutMapping("/{id}/accept")
    public ResponseEntity<?> acceptInvitation(@PathVariable Long id, Authentication auth) {
        try {
            invitationService.acceptInvitation(id, auth.getName());
            return ResponseEntity.ok(Map.of("message", "Invitation accepted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    /** Decline an invitation. */
    @PutMapping("/{id}/decline")
    public ResponseEntity<?> declineInvitation(@PathVariable Long id, Authentication auth) {
        try {
            invitationService.declineInvitation(id, auth.getName());
            return ResponseEntity.ok(Map.of("message", "Invitation declined"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }
}
