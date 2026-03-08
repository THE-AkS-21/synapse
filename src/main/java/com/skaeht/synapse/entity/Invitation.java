package com.skaeht.synapse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Invitation entity — represents a pending room invite or DM invite sent from
 * one user to another.
 * The recipient is identified by displayId so there's no exposure of internal
 * user IDs.
 */
@Entity
@Table(name = "invitations", indexes = {
        @Index(name = "idx_invitation_to_user", columnList = "to_username"),
        @Index(name = "idx_invitation_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The room being invited to (null for DM invitations) */
    @Column(name = "room_id", length = 255)
    private String roomId;

    /** Room name for display purposes */
    @Column(name = "room_name", length = 100)
    private String roomName;

    @Column(name = "from_username", nullable = false, length = 100)
    private String fromUsername;

    @Column(name = "to_username", nullable = false, length = 100)
    private String toUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private InvitationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }

    public enum InvitationType {
        ROOM, // Invited to join a room
        DM // Invited to start a direct message conversation
    }

    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        DECLINED
    }
}
