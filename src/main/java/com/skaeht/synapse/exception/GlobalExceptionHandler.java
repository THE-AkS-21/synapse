package com.skaeht.synapse.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * ARCHITECTURE NOTE: Centralized Error Handling (API Gateway Pattern)
 * This class acts as a global interceptor for all uncaught exceptions thrown during
 * HTTP request processing.
 * * Goals:
 * 1. Security: Prevents raw stack traces (which can leak database schema or internal logic)
 * from reaching the frontend.
 * 2. Consistency: Ensures the frontend always receives a predictable JSON error schema (ApiError),
 * regardless of where or why the backend failed.
 * 3. Observability: Standardizes warning/error logging for easier ingestion into Datadog/ELK stacks.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles requested entities (Users, Rooms, Messages) that do not exist.
     * Yields a 404 Not Found.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource Not Found: {} - {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Handles domain validation failures and illegal state transitions.
     * Yields a 400 Bad Request.
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        log.warn("Bad Request: {} - {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Handles custom business-logic authorization failures (e.g., trying to delete someone else's message).
     * Yields a 403 Forbidden.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleSecurityException(SecurityException ex, HttpServletRequest request) {
        log.warn("Security Violation: {} - {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Handles Spring Security native access denied exceptions (Missing roles/authorities).
     * Yields a 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access Denied: {} - {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", request.getRequestURI());
    }

    /**
     * Catch-all fallback for unhandled server-side crashes (NullPointers, DB Connection drops).
     * Yields a 500 Internal Server Error.
     * CRITICAL: Never pass 'ex.getMessage()' to the client here to avoid exposing internal system state.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("CRITICAL: Unhandled Server Exception at {}", request.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred.", request.getRequestURI());
    }

    // --- DRY HELPER --- //

    private ResponseEntity<ApiError> buildErrorResponse(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, path));
    }

    /**
     * Standardized JSON Error Payload.
     */
    @Data
    @AllArgsConstructor
    public static class ApiError {
        private int status;
        private String message;
        private String path;
        private final String timestamp = Instant.now().toString(); // ISO-8601 UTC format
    }
}