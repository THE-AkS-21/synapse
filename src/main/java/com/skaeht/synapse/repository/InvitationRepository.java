package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ARCHITECTURE NOTE: Connection Handshake Store
 * Handles the asynchronous invitation flow. This table is highly volatile
 * (records are frequently inserted and updated from PENDING to ACCEPTED/DECLINED).
 */
@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /**
     * Used to populate the notification tray for a specific user.
     * Requires a composite index on (to_username, status, created_at) to avoid full table scans.
     */
    List<Invitation> findByToUsernameAndStatusOrderByCreatedAtDesc(
            String toUsername, Invitation.InvitationStatus status);

    /**
     * PERFORMANCE NOTE:
     * Using existsBy... is vastly superior to findBy... when validating duplicate requests.
     * Spring Data JPA translates this into a `SELECT 1 ... LIMIT 1` query, which short-circuits
     * the database engine the moment a match is found, saving memory and CPU.
     */
    boolean existsByRoomIdAndToUsernameAndStatus(
            String roomId, String toUsername, Invitation.InvitationStatus status);
}