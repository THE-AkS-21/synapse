package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Message;
import org.springframework.data.domain.Page;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Find messages by sender username with pagination.
     */
    Page<Message> findBySenderUsername(String senderUsername, Pageable pageable);

    /**
     * Delete messages older than a specific timestamp.
     * Useful for cleanup operations.
     */
    long deleteByTimestampBefore(long timestamp);

    /**
     * Find messages by room ID, ordered by timestamp descending.
     */
    List<Message> findByRoomIdOrderByTimestampDesc(String roomId);
}