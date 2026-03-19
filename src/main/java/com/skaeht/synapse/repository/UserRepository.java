package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ARCHITECTURE NOTE: Identity and Profile Store
 * Manages authentication credentials and public profiles. Read-heavy, heavily cached at the Service layer.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByDisplayId(String displayId);

    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    /**
     * Basic exact or partial match via standard SQL LIKE.
     * Suitable for simple wildcard searches.
     */
    List<User> findByUsernameContainingIgnoreCase(String username);

    /**
     * PERFORMANCE NOTE: PostgreSQL Trigram Fuzzy Search
     * A highly advanced native query utilizing the 'pg_trgm' extension.
     * Unlike standard LIKE '%query%', trigram search breaks strings into 3-letter chunks
     * to find similarity. This allows the system to find "Jonathon" even if the user
     * typographically fumbles and searches for "Jonothan".
     * * Requirement: CREATE EXTENSION IF NOT EXISTS pg_trgm;
     * Indexing: CREATE INDEX trgm_idx_username ON users USING GIN (username gin_trgm_ops);
     */
    @Query(value = "SELECT * FROM users WHERE username % :query ORDER BY similarity(username, :query) DESC LIMIT 10", nativeQuery = true)
    List<User> searchByUsernameTrigram(@Param("query") String query);
}