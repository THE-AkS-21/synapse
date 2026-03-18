package com.skaeht.synapse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaeht.synapse.dto.response.AuthResponse;
import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.dto.request.RegisterRequest;
import com.skaeht.synapse.dto.request.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
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

import org.junit.jupiter.api.Disabled;

@Disabled("Docker not available")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret",
                () -> "test-secret-key-for-jwt-token-provider-integration-tests-should-be-at-least-256-bits-long");
        registry.add("jwt.expiration", () -> "3600000");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private String jwtToken;
    private String username = "testuser" + System.currentTimeMillis();

    @BeforeEach
    void setUp() throws Exception {
        this.stompClient = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        RegisterRequest registerRequest = new RegisterRequest(username, "password123", username + "@test.com");
        restTemplate.postForEntity("/api/auth/register", registerRequest, String.class);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest(username, "password123"),
                AuthResponse.class);

        assertNotNull(loginResponse.getBody(), "Login response should not be null");
        jwtToken = loginResponse.getBody().token();
        assertNotNull(jwtToken, "JWT token should not be null");
    }

    @Test
    void testSendAndReceiveMessage() throws Exception {
        BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>();

        String wsUrl = String.format("ws://localhost:%d/ws", port);

        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + jwtToken);

        StompSession session = stompClient.connectAsync(
                wsUrl,
                (WebSocketHttpHeaders) null,
                stompHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        session.subscribe("/topic/chat/general", new StompFrameHandler() {
                            @Override
                            public Type getPayloadType(StompHeaders headers) {
                                return ChatMessage.class;
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                messageQueue.add((ChatMessage) payload);
                            }
                        });
                    }
                }).get(5, TimeUnit.SECONDS);

        Thread.sleep(100);

        ChatMessage messageToSend = new ChatMessage("general", 1L, username, "Hello, World!", System.currentTimeMillis());
        session.send("/app/chat.sendMessage", messageToSend);

        ChatMessage receivedMessage = messageQueue.poll(10, TimeUnit.SECONDS);

        assertNotNull(receivedMessage, "Did not receive message from WebSocket");
        assertEquals("Hello, World!", receivedMessage.getContent());
        assertEquals(username, receivedMessage.getSenderUsername());
        assertTrue(receivedMessage.getTimestamp() > 0);

        session.disconnect();
    }
}