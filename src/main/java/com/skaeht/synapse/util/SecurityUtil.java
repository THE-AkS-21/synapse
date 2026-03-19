package com.skaeht.synapse.util;

import org.springframework.security.core.Authentication;

/**
 * Utility class for handling Spring Security Context operations.
 */
public class SecurityUtil {

    /**
     * Extracts the email (subject) from the current authentication token.
     * * @param authentication The Spring Security authentication object
     * @return The user's email, or throws an exception if unauthenticated
     */
    public static String getCurrentUserEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated user found in the security context.");
        }
        return authentication.getName();
    }
}