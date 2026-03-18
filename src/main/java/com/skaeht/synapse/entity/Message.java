package com.skaeht.synapse.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false, length = 36, unique = true)
    private String messageId; // UUID for deduplication

    // NORMALIZATION: Database now knows this strictly belongs to a Room
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // NORMALIZATION: Database now enforces this must be a valid User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false)
    private long timestamp;

    // ISOLATION: Flag for "Deleted for Everyone"
    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;
}