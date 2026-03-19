package com.skaeht.synapse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ARCHITECTURE NOTE: Connection Handshake Store
 * Represents a pending, accepted, or declined invitation.
 * By storing `fromUsername` and `toUsername` as Strings rather than hard Foreign Keys
 * to the User table, we heavily optimize read performance for the notification tray
 * (avoiding expensive JOINs just to show the inviter's name).
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

    @Column(name = "room_id", length = 255, updatable = false)
    private String roomId;

    @Column(name = "room_name", length = 100, updatable = false)
    private String roomName;

    @Column(name = "from_username", nullable = false, length = 100, updatable = false)
    private String fromUsername;

    @Column(name = "to_username", nullable = false, length = 100, updatable = false)
    private String toUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private InvitationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum InvitationType {
        ROOM,
        DM
    }

    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        DECLINED
    }
}