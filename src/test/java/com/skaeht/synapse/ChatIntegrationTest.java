package com.skaeht.synapse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.response.AuthResponse;
import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.dto.request.RegisterRequest;
import com.skaeht.synapse.dto.request.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ARCHITECTURE NOTE: Full-Stack Integration Testing
 * This suite spins up an entire Spring Boot context alongside actual PostgreSQL and Redis
 * instances using Testcontainers. It acts as the ultimate verification that our STOMP over
 * WebSocket configuration, JWT Authentication, and Redis Pub/Sub backplane are all playing
 * together correctly in a production-like environment.
 * * Note: Disabled by default to prevent CI/CD pipeline failures in environments lacking Docker daemons.
 */
@Disabled("Docker not available")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private String jwtToken;
    private String username;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret", () -> "test-secret-key-for-jwt-token-provider-integration-tests-should-be-at-least-256-bits-long");
        registry.add("jwt.expiration", () -> "3600000");
    }

    @BeforeEach
    void setUp() {
        this.stompClient = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        this.username = "testuser" + System.currentTimeMillis();
        this.jwtToken = registerAndLoginUser(username, "password123");
    }

    @Test
    void testSendAndReceiveMessage() throws Exception {
        BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>();
        String wsUrl = String.format("ws://localhost:%d/ws", port);

        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + jwtToken);

        // DRY FIX: Extracted the deeply nested anonymous classes into a clean connection block
        StompSession session = stompClient.connectAsync(
                wsUrl,
                (WebSocketHttpHeaders) null,
                stompHeaders,
                new TestSessionHandler(messageQueue, "/topic/chat/general")
        ).get(5, TimeUnit.SECONDS);

        // Allow time for the STOMP subscription to register with the broker
        Thread.sleep(100);

        ChatMessage messageToSend = new ChatMessage("general", 1L, username, "Hello, World!", System.currentTimeMillis());
        session.send("/app/room/general", messageToSend); // Note: Make sure this matches your ChatController @MessageMapping

        ChatMessage receivedMessage = messageQueue.poll(10, TimeUnit.SECONDS);

        assertNotNull(receivedMessage, "CRITICAL: Did not receive message from WebSocket broker");
        assertEquals("Hello, World!", receivedMessage.getContent());
        assertEquals(username, receivedMessage.getSenderUsername());
        assertTrue(receivedMessage.getTimestamp() > 0);

        session.disconnect();
    }

    // --- DRY HELPER METHODS --- //

    /** Helper to bootstrap a valid user session for the WebSocket test */
    private String registerAndLoginUser(String username, String password) {
        restTemplate.postForEntity("/api/auth/register",
                new RegisterRequest(username, password, username + "@test.com"), String.class);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest(username, password),
                AuthResponse.class);

        assertNotNull(loginResponse.getBody(), "Login response should not be null");
        return loginResponse.getBody().token();
    }

    /** Helper to cleanly handle STOMP subscriptions without deeply nested anonymous classes */
    private static class TestSessionHandler extends StompSessionHandlerAdapter {
        private final BlockingQueue<ChatMessage> queue;
        private final String topic;

        public TestSessionHandler(BlockingQueue<ChatMessage> queue, String topic) {
            this.queue = queue;
            this.topic = topic;
        }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            session.subscribe(topic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ChatMessage.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    queue.add((ChatMessage) payload);
                }
            });
        }
    }
}