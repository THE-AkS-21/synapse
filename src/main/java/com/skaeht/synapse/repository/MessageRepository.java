package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * ARCHITECTURE NOTE: High-Volume Message Store
 * This is the heaviest table in the application. All custom queries here must be backed
 * by appropriate database indexes.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Look up by public UUID String instead of internal Long
     */
    Optional<Message> findByMessageId(String messageId);
    /**
     * Used for loading initial room history on client join.
     * CRITICAL: Requires a composite index on (room_id, timestamp) to prevent
     * expensive 'filesort' operations in PostgreSQL.
     */
    List<Message> findByRoomIdOrderByTimestampAsc(String roomId);

    /**
     * Used by the REST controller to fetch recent history before reversing for UI rendering.
     */
    List<Message> findByRoomIdOrderByTimestampDesc(String roomId);

    /**
     * Fetches only active messages.
     * Useful if the client doesn't want to render "This message was deleted" tombstones.
     */
    List<Message> findByRoomIdAndIsDeletedFalseOrderByTimestampAsc(String roomId);

    /**
     * Cascading cleanup when a room is destroyed.
     * Translated directly to a bulk DELETE FROM messages WHERE room_id = ?
     * bypassing the persistence context for speed.
     */
    void deleteByRoomId(String roomId);

    /**
     * Data Retention execution hook.
     * Used by MessageCleanupService to purge stale data asynchronously.
     */
    long deleteByTimestampBefore(long timestamp);

}