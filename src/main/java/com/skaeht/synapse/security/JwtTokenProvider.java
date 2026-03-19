package com.skaeht.synapse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ARCHITECTURE NOTE: Cryptographic Token Provider
 * Currently utilizes HS256 (Symmetric Encryption), meaning the same secret key is used to
 * both sign and verify tokens.
 * FUTURE SCALING: If Synapse is split into multiple microservices, this should be upgraded
 * to RS256 (Asymmetric Encryption) so internal microservices can verify tokens using a Public Key
 * without needing access to the Private Key.
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationInMs;

    private Key key;

    @PostConstruct
    public void init() {
        // Ensures the secret is cryptographically strong enough for HMAC-SHA algorithms
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authentication cannot be null");
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return buildToken(new HashMap<>(), userDetails.getUsername());
    }

    public String generateToken(String email) {
        return buildToken(new HashMap<>(), email);
    }

    private String buildToken(Map<String, Object> claims, String subjectEmail) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subjectEmail)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    /**
     * Validates both the cryptographic signature and the expiration time.
     * Prevents replay attacks from expired tokens.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}