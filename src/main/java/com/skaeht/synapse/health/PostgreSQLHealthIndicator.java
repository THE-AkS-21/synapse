package com.skaeht.synapse.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * ARCHITECTURE NOTE: Database Readiness Probe
 * While Spring Boot provides a default DataSource health check, defining an explicit
 * PostgreSQL indicator allows us to map custom metadata (like the active catalog) directly
 * into our monitoring stack. This is critical for Kubernetes readiness probes to ensure
 * traffic isn't routed to a pod before the HikariCP connection pool is fully initialized.
 */
@Slf4j
@Component("postgresql")
@RequiredArgsConstructor
public class PostgreSQLHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Override
    public Health health() {
        // PERFORMANCE NOTE: Utilizing nested try-with-resources guarantees that both the
        // Statement and Connection are safely returned to the connection pool, preventing
        // connection exhaustion under high-frequency monitoring (e.g., Prometheus scraping every 5s).
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            ResultSet resultSet = statement.executeQuery("SELECT 1");

            if (resultSet.next() && resultSet.getInt(1) == 1) {
                return Health.up()
                        .withDetail("status", "PostgreSQL is UP")
                        .withDetail("database", connection.getCatalog())
                        .build();
            }

            return Health.down()
                    .withDetail("status", "Query execution failed")
                    .build();

        } catch (Exception e) {
            log.error("PostgreSQL health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("status", "PostgreSQL connection failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}