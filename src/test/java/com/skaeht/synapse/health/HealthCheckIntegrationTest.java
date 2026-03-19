package com.skaeht.synapse.health;

import com.skaeht.synapse.service.MessageBufferService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ARCHITECTURE NOTE: CI/CD Health Check Verification
 * This integration test ensures the Spring Boot Actuator endpoint is exposed and correctly
 * aggregates our custom liveness/readiness probes.
 * * We use @MockitoBean (Spring Boot 3.4+) to sever external infrastructure boundaries
 * (Redis, Postgres, WebSockets). This prevents the test from failing in CI/CD pipeline
 * environments where these external databases might not be actively running, ensuring
 * pipeline stability while still verifying the HTTP routing and JSON aggregation logic.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Bypasses JWT Auth Filter for isolated actuator testing
@ActiveProfiles("test")
public class HealthCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Mock heavy infrastructure clients to prevent ApplicationContext load failures
    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private MessageBufferService messageBufferService;

    // Mock the specific custom health indicators to cleanly control the aggregated state
    @MockitoBean
    private RedisHealthIndicator redisHealthIndicator;

    @MockitoBean
    private PostgreSQLHealthIndicator postgreSQLHealthIndicator;

    @MockitoBean
    private WebSocketHealthIndicator webSocketHealthIndicator;

    @Test
    public void testHealthEndpoint_WhenAllServicesUp_ShouldReturn200() throws Exception {

        // Arrange: Simulate a perfectly healthy infrastructure state
        when(redisHealthIndicator.health()).thenReturn(Health.up().build());
        when(postgreSQLHealthIndicator.health()).thenReturn(Health.up().build());
        when(webSocketHealthIndicator.health()).thenReturn(Health.up().build());

        // Act & Assert: Verify the Actuator correctly aggregates to HTTP 200 OK
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP")); // Strict payload validation
    }
}