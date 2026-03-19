package com.skaeht.synapse.controller;

import com.skaeht.synapse.entity.Invitation;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.exception.ResourceNotFoundException;
import com.skaeht.synapse.service.InvitationService;
import com.skaeht.synapse.service.UserService;
import com.skaeht.synapse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invitations")
@Slf4j
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final UserService userService;

    @GetMapping("/pending")
    public ResponseEntity<List<Invitation>> getPendingInvitations(Authentication authentication) {
        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return ResponseEntity.ok(invitationService.getPendingInvitations(user.getUsername()));
    }

    @PostMapping("/send")
    public ResponseEntity<Invitation> sendInvitation(
            @RequestParam String toUsername,
            @RequestParam(required = false) String roomId,
            Authentication authentication) {

        String senderEmail = SecurityUtil.getCurrentUserEmail(authentication);
        User sender = userService.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        /*
         * ARCHITECTURE NOTE:
         * We support legacy FE clients passing 'username' while new clients pass 'displayId'.
         * We chain the lookups using Optional.or() for clean evaluation.
         */
        User target = userService.findByDisplayId(toUsername)
                .or(() -> userService.findByUsername(toUsername))
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        Invitation invitation = invitationService.createInvitation(sender.getId(), target.getId(), roomId);
        return ResponseEntity.ok(invitation);
    }

    @PostMapping("/{invitationId}/respond")
    public ResponseEntity<Void> respondToInvitation(
            @PathVariable Long invitationId,
            @RequestParam boolean accept,
            Authentication authentication) {

        String email = SecurityUtil.getCurrentUserEmail(authentication);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        invitationService.respondToInvitation(invitationId, accept, user.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/room/{roomId}/invite")
    public ResponseEntity<String> inviteUser(
            Authentication authentication,
            @PathVariable String roomId,
            @RequestParam String targetDisplayId) {

        String senderEmail = SecurityUtil.getCurrentUserEmail(authentication);

        User sender = userService.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        User target = userService.findByDisplayId(targetDisplayId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        if (sender.getId().equals(target.getId())) {
            return ResponseEntity.badRequest().body("You cannot invite yourself.");
        }

        invitationService.sendRoomInvitation(roomId, sender.getId(), target.getId());
        return ResponseEntity.ok("Invitation sent successfully");
    }
}