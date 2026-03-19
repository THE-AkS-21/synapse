package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.Invitation;
import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.InvitationRepository;
import com.skaeht.synapse.repository.RoomRepository;
import com.skaeht.synapse.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Service for managing room and DM invitations.
 */
@Service
@Slf4j
public class InvitationService {

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Unified method called by the controller to route to the correct invitation type.
     */
    @Transactional
    public Invitation createInvitation(Long senderId, Long targetId, String roomId) {
        if (roomId != null && !roomId.trim().isEmpty()) {
            return sendRoomInvitation(roomId, senderId, targetId);
        } else {
            return sendDMInvitation(senderId, targetId);
        }
    }

    /**
     * Unified method called by the controller to route the accept/decline action.
     */
    @Transactional
    public void respondToInvitation(Long invitationId, boolean accept, String username) {
        if (accept) {
            acceptInvitation(invitationId, username);
        } else {
            declineInvitation(invitationId, username);
        }
    }


    /**
     * Get all pending invitations for a user.
     */
    public List<Invitation> getPendingInvitations(String username) {
        return invitationRepository.findByToUsernameAndStatusOrderByCreatedAtDesc(
                username, Invitation.InvitationStatus.PENDING);
    }

    /**
     * Accept an invitation — adds user to room (for ROOM type) or creates DM room
     * (for DM type).
     */
    @Transactional
    public void acceptInvitation(Long invitationId, String username) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getToUsername().equals(username)) {
            throw new IllegalStateException("Not your invitation");
        }

        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation already handled");
        }

        if (invitation.getType() == Invitation.InvitationType.ROOM) {
            Room room = roomRepository.findById(invitation.getRoomId())
                    .orElseThrow(() -> new IllegalArgumentException("Room no longer exists"));
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            room.getParticipants().add(user);
            roomRepository.save(room);
        }
        // DM type: the frontend will call POST /api/v1/rooms with type DIRECT

        invitation.setStatus(Invitation.InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
        log.info("Invitation {} accepted by {}", invitationId, username);
    }

    /**
     * Decline an invitation.
     */
    @Transactional
    public void declineInvitation(Long invitationId, String username) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getToUsername().equals(username)) {
            throw new IllegalStateException("Not your invitation");
        }

        invitation.setStatus(Invitation.InvitationStatus.DECLINED);
        invitationRepository.save(invitation);
        log.info("Invitation {} declined by {}", invitationId, username);
    }

    /**
     * Send a room invitation to a user securely via their Long IDs.
     */
    @Transactional
    public Invitation sendRoomInvitation(String roomId, Long senderId, Long targetId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        User fromUser = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        User targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        // Use creatorId for permission check
        if (room.getCreator() != null && !room.getCreator().getId().equals(fromUser.getId())) {
            throw new IllegalStateException("Only the room creator can invite members");
        }

        if (targetUser.getId().equals(fromUser.getId())) {
            throw new IllegalArgumentException("Cannot invite yourself");
        }

        // Check for duplicate pending invite
        if (invitationRepository.existsByRoomIdAndToUsernameAndStatus(
                roomId, targetUser.getUsername(), Invitation.InvitationStatus.PENDING)) {
            throw new IllegalStateException("Invitation already sent to this user");
        }

        Invitation invitation = Invitation.builder()
                .roomId(roomId)
                .roomName(room.getName())
                .fromUsername(fromUser.getUsername()) // Safely mapped from the DB entity
                .toUsername(targetUser.getUsername()) // Safely mapped from the DB entity
                .type(Invitation.InvitationType.ROOM)
                .build();

        Invitation saved = invitationRepository.save(invitation);

        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "INVITATION_RECEIVED", "targetId", targetUser.getId(), "fromUsername", fromUser.getUsername()));

        log.info("Room invitation sent from {} to {} for room {}", fromUser.getUsername(), targetUser.getUsername(), roomId);
        return saved;
    }

    /**
     * Send a DM invitation securely via Long IDs.
     */
    @Transactional
    public Invitation sendDMInvitation(Long senderId, Long targetId) {
        User fromUser = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        User targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        if (targetUser.getId().equals(fromUser.getId())) {
            throw new IllegalArgumentException("Cannot invite yourself");
        }

        Invitation invitation = Invitation.builder()
                .fromUsername(fromUser.getUsername())
                .toUsername(targetUser.getUsername())
                .type(Invitation.InvitationType.DM)
                .build();

        Invitation saved = invitationRepository.save(invitation);
        messagingTemplate.convertAndSend("/topic/global-events",
                Map.of("type", "INVITATION_RECEIVED", "targetId", targetUser.getId(), "fromUsername", fromUser.getUsername()));
        log.info("DM invitation sent from {} to {}", fromUser.getUsername(), targetUser.getUsername());
        return saved;
    }
}