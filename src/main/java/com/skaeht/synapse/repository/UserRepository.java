package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Look up a user by their human-readable display ID (for invite flows) */
    Optional<User> findByDisplayId(String displayId);

    // method for the unified search in LeftSidebar
    List<User> findByUsernameContainingIgnoreCase(String query);
}