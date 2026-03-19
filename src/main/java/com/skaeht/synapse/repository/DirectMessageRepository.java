package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.DirectMessage;
import com.skaeht.synapse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ARCHITECTURE NOTE: Private Messaging Persistence
 * Manages 1-on-1 private conversations separately from the main Room message flow.
 * Isolating this table allows for future horizontal partitioning (sharding) by user IDs
 * if private messaging scales significantly faster than group rooms.
 */
@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    /**
     * PERFORMANCE NOTE:
     * We pass User entities (specifically Hibernate Proxies generated via getReferenceById)
     * instead of raw Long IDs. This maintains strict type safety in JPQL without triggering
     * a preliminary SELECT statement to fetch the user objects.
     * * Requires an index on (sender_id, receiver_id, timestamp) for optimal performance.
     */
    @Query("SELECT dm FROM DirectMessage dm WHERE " +
            "(dm.sender = :user1 AND dm.receiver = :user2) OR " +
            "(dm.sender = :user2 AND dm.receiver = :user1) " +
            "ORDER BY dm.timestamp ASC")
    List<DirectMessage> findConversation(@Param("user1") User user1, @Param("user2") User user2);
}