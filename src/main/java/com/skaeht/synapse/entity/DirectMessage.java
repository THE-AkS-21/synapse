package com.skaeht.synapse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "direct_messages", indexes = {
        @Index(name = "idx_dm_sender_receiver", columnList = "sender_id, receiver_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Already strictly normalized!
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    private Instant timestamp = Instant.now();

    // ISOLATION: Flag for "Deleted for Everyone"
    @Column(nullable = false)
    @Builder.Default
    private boolean isDeleted = false;
}