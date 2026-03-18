package com.skaeht.synapse.security;

import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for loading user details during authentication.
 * REFACTORED: Exclusively utilizes email for database querying and Redis caching.
 */
@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    // Cache strictly keys off the email now
    private static final String REDIS_KEY_PREFIX = "user:session:";

    public UserDetailsServiceImpl(
            UserRepository userRepository,
            @Qualifier("objectRedisTemplate") RedisTemplate<String, Object> objectRedisTemplate) {
        this.userRepository = userRepository;
        this.objectRedisTemplate = objectRedisTemplate;
    }

    /**
     * Note: Overrides standard interface method, but the parameter 'email' is expected here.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String cacheKey = REDIS_KEY_PREFIX + email;

        // 1. Check Redis Cache
        try {
            Object cachedData = objectRedisTemplate.opsForValue().get(cacheKey);
            if (cachedData instanceof UserDetails) {
                log.debug("User cache HIT for email: {}", email);
                return (UserDetails) cachedData;
            }
        } catch (Exception e) {
            log.warn("Redis cache error during user lookup for {}. Falling back to DB. Error: {}", email, e.getMessage());
        }

        // 2. DB Fallback (Strictly via Email)
        log.debug("User cache MISS for email: {}. Querying DB.", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + email));

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);

        // 3. Populate Cache
        try {
            objectRedisTemplate.opsForValue().set(cacheKey, userDetails, Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Failed to update Redis cache for user {}. Error: {}", email, e.getMessage());
        }

        return userDetails;
    }

    /**
     * Evict the specific email session from Redis.
     */
    public void evictUserCache(String email) {
        try {
            if (email != null) {
                objectRedisTemplate.delete(REDIS_KEY_PREFIX + email);
                log.info("Evicted cache for user email: {}", email);
            }
        } catch (Exception e) {
            log.error("Failed to evict cache for user email: {}", email, e);
        }
    }
}