package com.skaeht.synapse.service;

import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.security.UserDetailsServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * Service for managing user-related operations.
 * Enforces email-centric queries and caching.
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

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

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
                .displayId(generateDisplayId())
                .build();

        return userRepository.save(user);
    }

    private String generateDisplayId() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(14);
            for (int i = 0; i < 14; i++) {
                if (i == 4 || i == 9) sb.append('-');
                else sb.append(DISPLAY_ID_CHARS.charAt(SECURE_RANDOM.nextInt(DISPLAY_ID_CHARS.length())));
            }
            candidate = sb.toString();
        } while (userRepository.findByDisplayId(candidate).isPresent());
        return candidate;
    }

    /**
     * Cache uses EMAIL strictly as requested.
     */
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

    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    public long getTotalUserCount() {
        return userRepository.count();
    }

    @Transactional
    @CacheEvict(value = "users", key = "#email")
    public boolean updatePassword(String email, String newPassword) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            // Sync: Evict the session cache as well
            userDetailsService.evictUserCache(email);

            log.info("Password updated and cache evicted for email: {}", email);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean updateUsername(String email, String newUsername) {
        if (userRepository.existsByUsername(newUsername)) return false;

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setUsername(newUsername);
            userRepository.save(user);

            // Sync: Evict the session cache to refresh details payload on next request
            userDetailsService.evictUserCache(email);
            return true;
        }
        return false;
    }
}