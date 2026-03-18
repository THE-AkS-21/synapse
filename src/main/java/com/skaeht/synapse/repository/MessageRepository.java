package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByRoomIdOrderByTimestampAsc(String roomId);
    List<Message> findByRoomIdOrderByTimestampDesc(String roomId); // Added for MessageController
    List<Message> findByRoomIdAndIsDeletedFalseOrderByTimestampAsc(String roomId);
    void deleteByRoomId(String roomId);
    long deleteByTimestampBefore(long timestamp); // Added for MessageCleanupService
}