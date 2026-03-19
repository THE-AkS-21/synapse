package com.skaeht.synapse.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * ARCHITECTURE NOTE: High-Volume Message Store
 * This is the heaviest table in the application. Indexes are heavily optimized for
 * time-series retrieval (fetching history for a specific room).
 */
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_room_timestamp", columnList = "room_id, timestamp"),
        @Index(name = "idx_sender_timestamp", columnList = "sender_id, timestamp"),
        @Index(name = "idx_message_id", columnList = "messageId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Client-generated UUID (Idempotency Key).
     * Prevents duplicate messages if a client drops network and retries a send request
     * that the server actually processed.
     */
    @Column(nullable = false, length = 36, unique = true, updatable = false)
    private String messageId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false, updatable = false)
    private long timestamp;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;
}