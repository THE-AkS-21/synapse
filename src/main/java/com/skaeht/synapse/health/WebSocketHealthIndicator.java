package com.skaeht.synapse.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ARCHITECTURE NOTE: WebSocket Saturation Metric
 * This class serves a dual purpose: a health check and a lightweight metric exporter.
 * By exposing the current active STOMP session count, infrastructure load balancers
 * (like AWS ALB or NGINX) can make intelligent routing decisions, sending new users away
 * from pods that are approaching their maximum TCP file descriptor limits.
 */
@Slf4j
@Component("websocket")
@RequiredArgsConstructor
public class WebSocketHealthIndicator implements HealthIndicator {

    // Safely handles optional dependencies without risking NullPointerExceptions
    private final Optional<SimpUserRegistry> simpUserRegistry;

    @Override
    public Health health() {
        try {
            return simpUserRegistry.map(registry -> {
                int userCount = registry.getUserCount();

                return Health.up()
                        .withDetail("status", "WebSocket is UP")
                        .withDetail("activeConnections", userCount)
                        .build();

            }).orElseGet(() -> Health.up()
                    .withDetail("status", "WebSocket is UP (Registry Not Loaded)")
                    .withDetail("activeConnections", "N/A")
                    .build());

        } catch (Exception e) {
            log.error("WebSocket registry assessment failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("status", "WebSocket health check failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}