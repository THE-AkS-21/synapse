package com.skaeht.synapse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * ARCHITECTURE NOTE: Private Messaging Persistence
 * Separating Direct Messages from Room Messages prevents the main `messages` table
 * from bloating and allows us to shard or partition 1-on-1 chats on a different
 * physical node in the future if DM volume outpaces group chat volume.
 */
@Entity
@Table(name = "direct_messages", indexes = {
        @Index(name = "idx_dm_sender_receiver", columnList = "sender_id, receiver_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @ToString.Exclude is critical here. If Lombok generates a toString() that
     * touches this lazy-loaded entity, it will trigger an unexpected N+1 database query
     * just by logging the DirectMessage object.
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false, updatable = false)
    private User receiver;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    /**
     * SOFT DELETE PATTERN:
     * We never execute SQL DELETE commands. Instead, we flip this flag and optionally
     * scrub the `content` field. This maintains referential integrity for message threads
     * and allows the frontend to render a "Message was deleted" tombstone.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}