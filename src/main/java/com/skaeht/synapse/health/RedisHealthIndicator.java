package com.skaeht.synapse.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * ARCHITECTURE NOTE: Distributed Cache & Event Bus Liveness
 * Redis acts as the central nervous system for Synapse's multi-node WebSocket delivery.
 * If Redis becomes unresponsive, messages will not fan-out across instances. This probe
 * alerts orchestrators to immediately flag the node as degraded, potentially triggering
 * a circuit breaker or alert to PagerDuty before users notice dropped messages.
 */
@Slf4j
@Component("redis")
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        // SECURITY / MEMORY NOTE: Wrapping RedisConnection in a try-with-resources block
        // is mandatory. Failing to close manual Redis connections will cause severe memory
        // leaks and quickly exhaust the Redis server's max client connection limit.
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {

            String pong = connection.ping();

            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up()
                        .withDetail("status", "Redis is UP")
                        .withDetail("ping", pong)
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "Redis PING failed")
                        .withDetail("response", pong)
                        .build();
            }

        } catch (Exception e) {
            log.error("Redis health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("status", "Redis connection failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}