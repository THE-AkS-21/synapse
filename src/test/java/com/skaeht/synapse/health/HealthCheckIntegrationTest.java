package com.skaeht.synapse.health;

import com.skaeht.synapse.service.MessageBufferService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.actuate.health.Health;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class HealthCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private MessageBufferService messageBufferService;

    // Mock the custom health indicators so they always return UP in this test
    @MockitoBean
    private RedisHealthIndicator redisHealthIndicator;

    @MockitoBean
    private PostgreSQLHealthIndicator postgreSQLHealthIndicator;

    @MockitoBean
    private WebSocketHealthIndicator webSocketHealthIndicator;

    @Test
    public void testHealthEndpoint() throws Exception {
        // Force the custom indicators to report UP
        when(redisHealthIndicator.health()).thenReturn(Health.up().build());
        when(postgreSQLHealthIndicator.health()).thenReturn(Health.up().build());
        when(webSocketHealthIndicator.health()).thenReturn(Health.up().build());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}