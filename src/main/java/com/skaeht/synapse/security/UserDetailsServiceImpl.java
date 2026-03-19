package com.skaeht.synapse.security;

import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ARCHITECTURE NOTE: Read-Through Cached Identity Provider
 * Because the JwtAuthFilter calls `loadUserByUsername` on *every single request*,
 * hitting the PostgreSQL database here would create a massive bottleneck.
 * This service implements a strict Read-Through cache pattern using Redis to keep
 * authentication latency sub-millisecond.
 */
@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    private static final String REDIS_KEY_PREFIX = "user:session:";

    public UserDetailsServiceImpl(
            UserRepository userRepository,
            @Qualifier("objectRedisTemplate") RedisTemplate<String, Object> objectRedisTemplate) {
        this.userRepository = userRepository;
        this.objectRedisTemplate = objectRedisTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String cacheKey = REDIS_KEY_PREFIX + email;

        // Level 1: Check Redis Fast Path
        try {
            Object cachedData = objectRedisTemplate.opsForValue().get(cacheKey);
            if (cachedData instanceof UserDetails) {
                return (UserDetails) cachedData;
            }
        } catch (Exception e) {
            log.warn("Redis cache unavailable. Falling back to DB lookup for user: {}", email);
        }

        // Level 2: DB Lookup & Cache Hydration
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + email));

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);

        try {
            // Cache TTL prevents stale authority/role data from lingering indefinitely
            objectRedisTemplate.opsForValue().set(cacheKey, userDetails, Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Failed to hydrate Redis cache for user {}.", email);
        }

        return userDetails;
    }

    /**
     * Triggered by UserService during mutative operations (e.g., password change, username update).
     * Ensures the JWT filter immediately picks up the new state on the next request.
     */
    public void evictUserCache(String email) {
        try {
            if (email != null) {
                objectRedisTemplate.delete(REDIS_KEY_PREFIX + email);
                log.debug("Invalidated security cache for user: {}", email);
            }
        } catch (Exception e) {
            log.error("Failed to invalidate security cache for user: {}", email, e);
        }
    }
}