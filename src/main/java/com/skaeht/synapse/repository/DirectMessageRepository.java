package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.DirectMessage;
import com.skaeht.synapse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    // We pass User objects directly instead of raw IDs for strict type safety
    @Query("SELECT dm FROM DirectMessage dm WHERE " +
            "(dm.sender = :user1 AND dm.receiver = :user2) OR " +
            "(dm.sender = :user2 AND dm.receiver = :user1) " +
            "ORDER BY dm.timestamp ASC")
    List<DirectMessage> findConversation(@Param("user1") User user1, @Param("user2") User user2);
}