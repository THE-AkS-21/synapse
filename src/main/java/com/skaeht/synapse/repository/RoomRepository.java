package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Room;
import com.skaeht.synapse.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Room entity operations
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    /**
     * Find rooms by type
     */
    Page<Room> findByType(Room.RoomType type, Pageable pageable);

    /**
     * Find rooms where a specific user is a participant
     */
    @Query("SELECT r FROM Room r JOIN r.participants p WHERE p.id = :userId")
    List<Room> findByParticipant(@Param("userId") Long userId);

    /**
     * Find a room by name
     */
    Optional<Room> findByName(String name);

    /**
     * Check if a room exists with the given name
     */
    boolean existsByName(String name);

    @Query("""
        SELECT r FROM Room r
        JOIN r.participants p
        WHERE p.username = :username
    """)
    List<Room> findRoomsByUsername(@Param("username") String username);
    /**
     * Find direct message room between two users
     */
    @Query("SELECT r FROM Room r JOIN r.participants p1 JOIN r.participants p2 " +
            "WHERE r.type = 'DIRECT' AND p1.id = :user1Id AND p2.id = :user2Id")
    Optional<Room> findDirectMessageRoom(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
}
