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

    /**
     * Send a room invitation to a user identified by their display ID.
     * Only the room creator can invite users. Only PRIVATE rooms require
     * invitations.
     */
    /**
     * Send a room invitation to a user identified by their display ID.
     * Only the room creator can invite users. Only PRIVATE rooms require
     * invitations.
     */
    @Transactional
    public Invitation sendRoomInvitation(String roomId, String fromUsername, String toDisplayId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        // Get the inviting user to compare IDs
        User fromUser = userRepository.findByUsername(fromUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + fromUsername));

        // Use creatorId for permission check
        if (room.getCreatorId() != null && !room.getCreatorId().equals(fromUser.getId())) {
            throw new IllegalStateException("Only the room creator can invite members");
        }

        User targetUser = userRepository.findByDisplayId(toDisplayId)
                .orElseThrow(() -> new IllegalArgumentException("No user found with ID: " + toDisplayId));

        if (targetUser.getUsername().equals(fromUsername)) {
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
                .fromUsername(fromUsername)
                .toUsername(targetUser.getUsername())
                .type(Invitation.InvitationType.ROOM)
                .build();

        Invitation saved = invitationRepository.save(invitation);
        log.info("Room invitation sent from {} to {} for room {}", fromUsername, targetUser.getUsername(), roomId);
        return saved;
    }

    /**
     * Send a DM invitation to a user identified by their display ID.
     */
    @Transactional
    public Invitation sendDMInvitation(String fromUsername, String toDisplayId) {
        User targetUser = userRepository.findByDisplayId(toDisplayId)
                .orElseThrow(() -> new IllegalArgumentException("No user found with ID: " + toDisplayId));

        if (targetUser.getUsername().equals(fromUsername)) {
            throw new IllegalArgumentException("Cannot invite yourself");
        }

        Invitation invitation = Invitation.builder()
                .fromUsername(fromUsername)
                .toUsername(targetUser.getUsername())
                .type(Invitation.InvitationType.DM)
                .build();

        Invitation saved = invitationRepository.save(invitation);
        log.info("DM invitation sent from {} to {}", fromUsername, targetUser.getUsername());
        return saved;
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
}
