package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * Service for managing user-related operations.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String DISPLAY_ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Register a new user.
     *
     * @param username    The username
     * @param email       The email
     * @param rawPassword The raw password (will be encoded)
     * @return The created user
     * @throws IllegalArgumentException if username or email already exists
     */
    @Transactional
    public User registerUser(String username, String email, String rawPassword) {
        log.info("Registering new user: {}", username);

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
                .displayId(generateDisplayId())
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {} (displayId: {})", username, savedUser.getDisplayId());
        return savedUser;
    }

    /** Generate a unique alphanumeric display ID in format XXXX-XXXX-XXXX */
    private String generateDisplayId() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(14);
            for (int i = 0; i < 14; i++) {
                if (i == 4 || i == 9) {
                    sb.append('-');
                } else {
                    sb.append(DISPLAY_ID_CHARS.charAt(SECURE_RANDOM.nextInt(DISPLAY_ID_CHARS.length())));
                }
            }
            candidate = sb.toString();
        } while (userRepository.findByDisplayId(candidate).isPresent());
        return candidate;
    }

    /**
     * Find user by username with caching.
     *
     * @param username The username to search for
     * @return Optional containing the user if found
     */
    @Cacheable(value = "users", key = "#username")
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByDisplayId(String displayId) {
        return userRepository.findByDisplayId(displayId);
    }

    /**
     * Find user by email.
     *
     * @param email The email to search for
     * @return Optional containing the user if found
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Check if username exists.
     *
     * @param username The username to check
     * @return true if exists, false otherwise
     */
    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Check if email exists.
     *
     * @param email The email to check
     * @return true if exists, false otherwise
     */
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Get total user count.
     *
     * @return Total number of registered users
     */
    public long getTotalUserCount() {
        return userRepository.count();
    }

    /**
     * Update user password.
     *
     * @param username    The username
     * @param newPassword The new password (raw)
     * @return true if updated successfully
     */
    @Transactional
    public boolean updatePassword(String username, String newPassword) {
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            log.info("Password updated for user: {}", username);
            return true;
        }

        return false;
    }

    /**
     * Update username.
     * 
     * @return true if updated, false if username taken
     */
    @Transactional
    public boolean updateUsername(String currentUsername, String newUsername) {
        if (userRepository.existsByUsername(newUsername)) {
            return false;
        }
        Optional<User> userOpt = userRepository.findByUsername(currentUsername);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setUsername(newUsername);
            userRepository.save(user);
            log.info("Username updated: {} -> {}", currentUsername, newUsername);
            return true;
        }
        return false;
    }
}
