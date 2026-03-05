package com.skaeht.synapse.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for WebSocket connections.
 * Reports the number of active WebSocket sessions.
 */
@Component("websocket")
public class WebSocketHealthIndicator implements HealthIndicator {

    @Autowired(required = false)
    private SimpUserRegistry simpUserRegistry;

    @Override
    public Health health() {
        try {
            if (simpUserRegistry != null) {
                int userCount = simpUserRegistry.getUserCount();

                return Health.up()
                        .withDetail("status", "WebSocket is UP")
                        .withDetail("activeConnections", userCount)
                        .build();
            } else {
                return Health.up()
                        .withDetail("status", "WebSocket is UP (registry not available)")
                        .withDetail("activeConnections", "N/A")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "WebSocket health check failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
