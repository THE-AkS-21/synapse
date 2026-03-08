package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    List<Invitation> findByToUsernameAndStatusOrderByCreatedAtDesc(
            String toUsername, Invitation.InvitationStatus status);

    boolean existsByRoomIdAndToUsernameAndStatus(
            String roomId, String toUsername, Invitation.InvitationStatus status);
}
