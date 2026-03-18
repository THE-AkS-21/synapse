package com.skaeht.synapse.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private final String testSecret = "TestSecretKeyForJwtTokenProviderUnitTests1234567890";
    private final long testExpiration = 60000;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationInMs", testExpiration);
        jwtTokenProvider.init();
    }

    @Test
    void testGenerateToken() {
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(email);

        assertNotNull(token);
        String emailFromToken = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(testSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();

        assertEquals(email, emailFromToken);
    }

    @Test
    void testValidateToken_Valid() {
        String token = jwtTokenProvider.generateToken("test@example.com");
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void testValidateToken_InvalidSignature() {
        String token = jwtTokenProvider.generateToken("test@example.com");
        assertFalse(jwtTokenProvider.validateToken(token + "a"));
    }

    @Test
    void testGetEmailFromToken() {
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(email);
        assertEquals(email, jwtTokenProvider.getEmailFromToken(token));
    }
}