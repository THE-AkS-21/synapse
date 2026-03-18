package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    // Newly added missing methods required by Services
    Optional<User> findByEmail(String email);
    Optional<User> findByDisplayId(String displayId);
    List<User> findByUsernameContainingIgnoreCase(String username);

    // Requires: CREATE EXTENSION IF NOT EXISTS pg_trgm; and the GIN index in DB
    @Query(value = "SELECT * FROM users WHERE username % :query ORDER BY similarity(username, :query) DESC LIMIT 10", nativeQuery = true)
    List<User> searchByUsernameTrigram(@Param("query") String query);
}