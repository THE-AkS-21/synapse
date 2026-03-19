package com.skaeht.synapse.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ARCHITECTURE NOTE: Cryptographic Boundary Testing
 * This test suite verifies the integrity of our stateless authentication mechanism.
 * It ensures that JWT generation, cryptographic signing (HMAC-SHA256), and
 * validation behave correctly.
 * * PERFORMANCE NOTE:
 * We intentionally do NOT use @SpringBootTest here. Bootstrapping the entire Spring
 * context for utility classes slows down the CI/CD pipeline. Instead, we manually
 * inject the @Value fields using ReflectionTestUtils, keeping test execution under a few milliseconds.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // The secret must be strictly >= 256 bits (32+ characters) for HS256 compliance
    private final String testSecret = "TestSecretKeyForJwtTokenProviderUnitTests1234567890";
    private final long testExpiration = 60000;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();

        // Manually inject configuration properties normally handled by Spring's environment context
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationInMs", testExpiration);
        jwtTokenProvider.init();
    }

    @Test
    void testGenerateToken() {
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(email);

        assertNotNull(token);

        // Independently parse the token using the raw JJWT library to ensure
        // the provider isn't producing a malformed or improperly signed payload.
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
        assertTrue(jwtTokenProvider.validateToken(token), "Token should be cryptographically valid and unexpired");
    }

    @Test
    void testValidateToken_InvalidSignature_ShouldReject() {
        String token = jwtTokenProvider.generateToken("test@example.com");

        // Simulates a Man-in-the-Middle (MITM) attack where the payload is tampered with,
        // or a token signed by a different environment's (e.g., DEV vs PROD) secret key.
        assertFalse(jwtTokenProvider.validateToken(token + "tampered"), "Tampered token should immediately fail signature verification");
    }

    @Test
    void testGetEmailFromToken() {
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(email);

        assertEquals(email, jwtTokenProvider.getEmailFromToken(token));
    }
}