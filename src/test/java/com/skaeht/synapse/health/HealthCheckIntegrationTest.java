package com.skaeht.synapse.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import com.skaeht.synapse.repository.UserRepository;
import com.skaeht.synapse.repository.MessageRepository;
import com.skaeht.synapse.repository.RoomRepository;
import org.redisson.api.RedissonClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for health check endpoints.
 * Tests the actuator health endpoints without requiring external dependencies.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, RedisAutoConfiguration.class })
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.show-details=always",
        "management.health.defaults.enabled=false"
})
class HealthCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MessageRepository messageRepository;

    @MockitoBean
    private RoomRepository roomRepository;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @MockitoBean
    private org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer;

    @Test
    @org.springframework.security.test.context.support.WithMockUser
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.status").exists());
    }
}
