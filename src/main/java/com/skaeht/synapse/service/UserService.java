package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.security.UserDetailsServiceImpl;
import com.skaeht.synapse.util.IdGeneratorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing user operations.
 * Enforces email-centric queries and aggressive caching for high-read endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsServiceImpl userDetailsService;

    @Transactional
    public User registerUser(String username, String email, String rawPassword) {
        log.info("Registering new user email: {}", email);

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .displayId(generateUniqueDisplayId())
                .build();

        return userRepository.save(user);
    }

    /**
     * Loops until a globally unique display ID is found.
     */
    private String generateUniqueDisplayId() {
        String candidate;
        do {
            candidate = IdGeneratorUtil.generateDisplayIdCandidate();
        } while (userRepository.findByDisplayId(candidate).isPresent());
        return candidate;
    }

    @Cacheable(value = "users", key = "#email")
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByDisplayId(String displayId) {
        return userRepository.findByDisplayId(displayId);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public boolean updatePassword(String email, String newPassword) {
        return userRepository.findByEmail(email).map(user -> {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            userDetailsService.evictUserCache(email); // Sync session cache
            log.info("Password updated and cache evicted for email: {}", email);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean updateUsername(String email, String newUsername) {
        if (userRepository.existsByUsername(newUsername)) return false;

        return userRepository.findByEmail(email).map(user -> {
            user.setUsername(newUsername);
            userRepository.save(user);
            userDetailsService.evictUserCache(email);
            return true;
        }).orElse(false);
    }
}