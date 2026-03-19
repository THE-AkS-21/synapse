package com.skaeht.synapse.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * ARCHITECTURE NOTE: API Gateway Rate Limiting
 * Protects the REST API endpoints from DDoS attacks and brute-force login attempts.
 * * CRITICAL FIX: The previous implementation used an unbounded ConcurrentHashMap to store IPs.
 * In a production environment, a distributed botnet pinging the server would fill up the map
 * with millions of unique IPs, resulting in a catastrophic OutOfMemory (OOM) crash.
 * This was refactored to use a bounded Caffeine cache that automatically evicts idle IP records.
 */
@Component
@Order(1) // Ensures this filter executes before Security filters to drop bad traffic instantly
public class RateLimitFilter implements Filter {

    // Bounded eviction cache prevents memory exhaustion from malicious IP spoofing
    private final Cache<String, Bucket> bucketCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private Bucket createNewBucket() {
        // Token bucket algorithm: 60 requests per minute burst capacity
        Bandwidth limit = Bandwidth.builder()
                .capacity(60)
                .refillGreedy(60, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private Bucket resolveBucket(String ip) {
        return bucketCache.get(ip, k -> createNewBucket());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // WebSocket frames are inherently rate-limited downstream by the ChatService logic.
        // We bypass the HTTP filter here to avoid interfering with the WS upgrade handshake.
        if (request.getRequestURI().startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Handle proxy headers (e.g., NGINX, AWS ALB) to ensure we rate-limit the true client IP
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }

        Bucket bucket = resolveBucket(ip);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
        }
    }
}