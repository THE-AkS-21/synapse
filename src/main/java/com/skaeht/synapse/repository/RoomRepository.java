package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Room;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param; // FIXED: Replaced incorrect lettuce import
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ARCHITECTURE NOTE: Room Aggregation Root
 * Manages the core entity that binds Users and Messages together.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    List<Room> findByType(Room.RoomType type);
    List<Room> findByCreatorId(Long creatorId);

    /**
     * Finds all rooms a specific user is a part of.
     * Used to populate the left sidebar upon user login.
     */
    List<Room> findByParticipants_Id(Long userId);

    /**
     * PERFORMANCE NOTE: The N+1 Query Solver
     * By default, @ManyToMany collections (participants) are lazy-loaded. If we fetch 50 rooms
     * and call room.getParticipants() on each, Hibernate executes 51 separate SQL queries.
     * @EntityGraph forces a single SQL LEFT OUTER JOIN, retrieving the Room and all its
     * Participants in one highly efficient network round-trip.
     */
    @EntityGraph(attributePaths = {"participants", "creator"})
    Optional<Room> findById(String id);

    /**
     * Self-referencing JOIN to locate a specific 1-on-1 Direct Message room.
     * Identifies a room of type DIRECT where both user1 and user2 exist in the participant junction table.
     */
    @Query("SELECT r FROM Room r JOIN r.participants p1 JOIN r.participants p2 " +
            "WHERE r.type = 'DIRECT' AND p1.id = :userId1 AND p2.id = :userId2")
    Optional<Room> findDirectMessageRoom(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}