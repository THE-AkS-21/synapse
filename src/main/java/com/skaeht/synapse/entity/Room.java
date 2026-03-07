package com.skaeht.synapse.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a chat room or channel.
 * Supports different room types: PUBLIC, PRIVATE, DIRECT
 */
@Entity
@Table(name = "rooms", indexes = {
        @Index(name = "idx_room_type", columnList = "type"),
        @Index(name = "idx_room_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @ManyToMany
    @JoinTable(name = "room_participants", joinColumns = @JoinColumn(name = "room_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private Set<User> participants = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Room type enumeration
     */
    public enum RoomType {
        PUBLIC, // Open to all users
        PRIVATE, // Invite-only
        DIRECT // One-to-one conversation
    }
}
