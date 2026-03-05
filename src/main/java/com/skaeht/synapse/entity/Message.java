package com.skaeht.synapse.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_room_timestamp", columnList = "roomId,timestamp"),
        @Index(name = "idx_sender_timestamp", columnList = "senderUsername,timestamp"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
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

    @Column(length = 255)
    private String roomId; // Room/channel ID for targeted messaging

    @Column(nullable = false)
    private String senderUsername;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false)
    private long timestamp;
}