package com.skaeht.synapse.repository;

import com.skaeht.synapse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // This is crucial for Spring Security's UserDetailsService
    Optional<User> findByUsername(String username);
}