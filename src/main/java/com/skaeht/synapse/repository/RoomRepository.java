package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Room;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    List<Room> findByType(Room.RoomType type);
    List<Room> findByCreatorId(Long creatorId);
    List<Room> findByParticipants_Id(Long userId);

    // BEST PRACTICE: Eagerly fetch the participants in a single query
    @EntityGraph(attributePaths = {"participants", "creator"})
    Optional<Room> findById(String id);

    @Query("SELECT r FROM Room r JOIN r.participants p1 JOIN r.participants p2 " +
            "WHERE r.type = 'DIRECT' AND p1.id = :userId1 AND p2.id = :userId2")
    Optional<Room> findDirectMessageRoom(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

}