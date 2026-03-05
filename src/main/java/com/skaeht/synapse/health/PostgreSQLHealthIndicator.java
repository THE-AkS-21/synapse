package com.skaeht.synapse.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Custom health indicator for PostgreSQL database.
 * Tests database connectivity and query execution.
 */
@Component("postgresql")
public class PostgreSQLHealthIndicator implements HealthIndicator {

    @Autowired
    private DataSource dataSource;

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {

            // Test query execution
            try (Statement statement = connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT 1");

                if (resultSet.next() && resultSet.getInt(1) == 1) {
                    return Health.up()
                            .withDetail("status", "PostgreSQL is UP")
                            .withDetail("database", connection.getCatalog())
                            .build();
                }
            }

            return Health.down()
                    .withDetail("status", "Query execution failed")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "PostgreSQL connection failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
