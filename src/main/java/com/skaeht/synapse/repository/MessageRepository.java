package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    // We can add methods to find messages by room, user, etc., later
}