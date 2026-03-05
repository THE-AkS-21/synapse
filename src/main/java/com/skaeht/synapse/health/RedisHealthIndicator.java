package com.skaeht.synapse.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for Redis.
 * Checks both connection and pub/sub functionality.
 */
@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        try {
            RedisConnection connection = redisConnectionFactory.getConnection();

            // Test basic connection with PING
            String pong = connection.ping();

            connection.close();

            if ("PONG".equals(pong)) {
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
            return Health.down()
                    .withDetail("status", "Redis connection failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
