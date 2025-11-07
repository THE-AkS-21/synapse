package com.skaeht.synapse.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // Use a test-safe secret and expiration
    private final String testSecret = "TestSecretKeyForJwtTokenProviderUnitTests1234567890";
    private final long testExpiration = 60000; // 1 minute

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        // Manually inject the properties using ReflectionTestUtils
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationInMs", testExpiration);
        jwtTokenProvider.init(); // Manually call the @PostConstruct method
    }

    @Test
    void testGenerateToken() {
        String username = "testUser";
        String token = jwtTokenProvider.generateToken(username);

        assertNotNull(token);
        String usernameFromToken = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(testSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();

        assertEquals(username, usernameFromToken);
    }

    @Test
    void testValidateToken_Valid() {
        String token = jwtTokenProvider.generateToken("testUser");
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void testValidateToken_InvalidSignature() {
        String token = jwtTokenProvider.generateToken("testUser");
        // Add a character to invalidate the signature
        assertFalse(jwtTokenProvider.validateToken(token + "a"));
    }

    @Test
    void testValidateToken_Expired() throws InterruptedException {
        // Create a provider with a 1ms expiration
        JwtTokenProvider expiredProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(expiredProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(expiredProvider, "jwtExpirationInMs", 1L); // 1 ms
        expiredProvider.init();

        String token = expiredProvider.generateToken("testUser");

        // Wait for the token to expire
        Thread.sleep(5);

        assertFalse(expiredProvider.validateToken(token));
    }

    @Test
    void testGetUsernameFromToken() {
        String username = "testSubject";
        String token = jwtTokenProvider.generateToken(username);
        assertEquals(username, jwtTokenProvider.getUsernameFromToken(token));
    }
}