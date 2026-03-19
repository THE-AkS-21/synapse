package com.skaeht.synapse.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * ARCHITECTURE NOTE: Room Aggregation Root
 * Acts as the aggregate root for all group interactions.
 * Note that the 'id' is a manually generated String (e.g., '1234-5678-9012') rather than
 * an auto-incrementing Long to prevent external attackers from scraping room IDs
 * sequentially.
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

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", updatable = false)
    private User creator;

    @Column(name = "theme", length = 50)
    @Builder.Default
    private String theme = "default";

    @JsonIgnore
    @ToString.Exclude
    @ManyToMany
    @JoinTable(
            name = "room_participants",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> participants = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum RoomType {
        PUBLIC,
        PRIVATE,
        DIRECT
    }
}