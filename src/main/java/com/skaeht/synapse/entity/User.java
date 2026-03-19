package com.skaeht.synapse.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * ARCHITECTURE NOTE: Core Identity Entity
 * * IMPORTANT JPA FIX: Replaced @Data with @Getter and @Setter.
 * Using @Data on a JPA entity generates equals() and hashCode() methods that evaluate
 * all fields. If this entity is ever placed in a HashSet (e.g., inside Room.participants),
 * it can trigger lazy-loading exceptions, infinite recursion, or massive performance drops.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    /**
     * Public-facing immutable identifier (e.g. XXXX-XXXX-XXXX).
     * Used for adding friends and invites without exposing the internal DB row ID.
     */
    @Column(unique = true, updatable = false)
    private String displayId;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    @ToString.Exclude
    @Column(nullable = false)
    private String password;

    private String avatarUrl;

    /**
     * Updated via a write-behind Redis cache to prevent hammering the DB
     * every time the user connects/disconnects.
     */
    private Instant lastSeen;
}