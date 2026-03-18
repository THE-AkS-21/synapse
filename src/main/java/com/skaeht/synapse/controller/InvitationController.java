package com.skaeht.synapse.controller;

import com.skaeht.synapse.entity.Invitation;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.exception.ResourceNotFoundException;
import com.skaeht.synapse.service.InvitationService;
import com.skaeht.synapse.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invitations")
@Slf4j
public class InvitationController {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserService userService;

    @GetMapping("/pending")
    public ResponseEntity<List<Invitation>> getPendingInvitations(Authentication authentication) {
        // Find receiver's actual username via their authenticated email
        var userOpt = userService.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(invitationService.getPendingInvitations(userOpt.get().getUsername()));
    }

    @PostMapping("/send")
    public ResponseEntity<Invitation> sendInvitation(
            @RequestParam String toUsername, // Can be username or displayId from legacy FE calls
            @RequestParam(required = false) String roomId,
            Authentication authentication) {

        // Find sender's actual user via their authenticated email
        var senderOpt = userService.findByEmail(authentication.getName());
        if (senderOpt.isEmpty()) return ResponseEntity.status(401).build();

        // Support both displayId (new standard) and username (legacy fallback)
        var targetOpt = userService.findByDisplayId(toUsername);
        if (targetOpt.isEmpty()) {
            targetOpt = userService.findByUsername(toUsername);
        }

        if (targetOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        // Pass the resolved Long IDs to the updated service method
        Invitation invitation = invitationService.createInvitation(
                senderOpt.get().getId(),
                targetOpt.get().getId(),
                roomId
        );
        return ResponseEntity.ok(invitation);
    }

    @PostMapping("/{invitationId}/respond")
    public ResponseEntity<Void> respondToInvitation(
            @PathVariable Long invitationId,
            @RequestParam boolean accept,
            Authentication authentication) {

        // Find receiver's actual username via their authenticated email
        var userOpt = userService.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();

        invitationService.respondToInvitation(invitationId, accept, userOpt.get().getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/room/{roomId}/invite")
    public ResponseEntity<String> inviteUser(
            Authentication authentication,
            @PathVariable String roomId,
            @RequestParam String targetDisplayId) {

        String currentEmail = authentication.getName();
        User sender = userService.findByEmail(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        User target = userService.findByDisplayId(targetDisplayId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        if (sender.getId().equals(target.getId())) {
            return ResponseEntity.badRequest().body("You cannot invite yourself.");
        }

        // FIXED: Renamed createRoomInvite to sendRoomInvitation
        invitationService.sendRoomInvitation(roomId, sender.getId(), target.getId());

        return ResponseEntity.ok("Invitation sent successfully");
    }
}