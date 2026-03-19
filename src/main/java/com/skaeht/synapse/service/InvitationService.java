package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Invitation;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.InvitationRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * ARCHITECTURE NOTE: Invitation State Machine
 * This service handles the lifecycle of asynchronous user connections (Room invites & DMs).
 * It acts as a strict gatekeeper, verifying ownership and preventing race conditions
 * (e.g., accepting an already declined invite) before touching the core Room aggregations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Invitation createInvitation(Long senderId, Long targetId, String roomId) {
        return (roomId != null && !roomId.trim().isEmpty())
                ? sendRoomInvitation(roomId, senderId, targetId)
                : sendDMInvitation(senderId, targetId);
    }

    @Transactional
    public void respondToInvitation(Long invitationId, boolean accept, String username) {
        if (accept) {
            acceptInvitation(invitationId, username);
        } else {
            declineInvitation(invitationId, username);
        }
    }

    @Transactional(readOnly = true)
    public List<Invitation> getPendingInvitations(String username) {
        return invitationRepository.findByToUsernameAndStatusOrderByCreatedAtDesc(
                username, Invitation.InvitationStatus.PENDING);
    }

    @Transactional
    public void acceptInvitation(Long invitationId, String username) {
        Invitation invitation = validateAndGetInvitation(invitationId, username, Invitation.InvitationStatus.PENDING);

        if (invitation.getType() == Invitation.InvitationType.ROOM) {
            Room room = roomRepository.findById(invitation.getRoomId())
                    .orElseThrow(() -> new IllegalArgumentException("Room no longer exists"));

            // Uses getReferenceById to avoid unnecessary SELECT queries when associating existing entities
            User user = userRepository.getReferenceById(
                    userRepository.findByUsername(username).orElseThrow().getId()
            );

            room.getParticipants().add(user);
            roomRepository.save(room);
        }

        // DM Type handling is deferred to the frontend routing POST /api/v1/rooms with type DIRECT
        invitation.setStatus(Invitation.InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        log.info("Invitation {} accepted by {}", invitationId, username);
    }

    @Transactional
    public void declineInvitation(Long invitationId, String username) {
        Invitation invitation = validateAndGetInvitation(invitationId, username, null);

        invitation.setStatus(Invitation.InvitationStatus.DECLINED);
        invitationRepository.save(invitation);

        log.info("Invitation {} declined by {}", invitationId, username);
    }

    @Transactional
    public Invitation sendRoomInvitation(String roomId, Long senderId, Long targetId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        User fromUser = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));
        User targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        if (room.getCreator() != null && !room.getCreator().getId().equals(fromUser.getId())) {
            throw new IllegalStateException("Only the room creator can invite members");
        }
        if (targetUser.getId().equals(fromUser.getId())) {
            throw new IllegalArgumentException("Cannot invite yourself");
        }
        if (invitationRepository.existsByRoomIdAndToUsernameAndStatus(
                roomId, targetUser.getUsername(), Invitation.InvitationStatus.PENDING)) {
            throw new IllegalStateException("Invitation already sent to this user");
        }

        Invitation saved = invitationRepository.save(Invitation.builder()
                .roomId(roomId)
                .roomName(room.getName())
                .fromUsername(fromUser.getUsername())
                .toUsername(targetUser.getUsername())
                .type(Invitation.InvitationType.ROOM)
                .build());

        broadcastInvitationEvent(targetUser.getId(), fromUser.getUsername());
        return saved;
    }

    @Transactional
    public Invitation sendDMInvitation(Long senderId, Long targetId) {
        User fromUser = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));
        User targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        if (targetUser.getId().equals(fromUser.getId())) {
            throw new IllegalArgumentException("Cannot invite yourself");
        }

        Invitation saved = invitationRepository.save(Invitation.builder()
                .fromUsername(fromUser.getUsername())
                .toUsername(targetUser.getUsername())
                .type(Invitation.InvitationType.DM)
                .build());

        broadcastInvitationEvent(targetUser.getId(), fromUser.getUsername());
        return saved;
    }

    // --- DRY Helpers ---

    private Invitation validateAndGetInvitation(Long invitationId, String username, Invitation.InvitationStatus requiredStatus) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getToUsername().equals(username)) {
            throw new SecurityException("Security Violation: Attempted to modify an invitation belonging to another user");
        }
        if (requiredStatus != null && invitation.getStatus() != requiredStatus) {
            throw new IllegalStateException("Invitation is no longer pending");
        }
        return invitation;
    }

    private void broadcastInvitationEvent(Long targetId, String fromUsername) {
        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "INVITATION_RECEIVED", "targetId", targetId, "fromUsername", fromUsername));
    }
}