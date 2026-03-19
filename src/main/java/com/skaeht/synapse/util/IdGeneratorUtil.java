package com.skaeht.synapse.util;

import java.security.SecureRandom;

/**
 * Utility for generating standardized, cryptographically secure IDs across the application.
 */
public class IdGeneratorUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DISPLAY_ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** Generates a room ID format: 1234-5678-9012 */
    public static String generateNumericRoomId() {
        return String.format("%04d-%04d-%04d",
                SECURE_RANDOM.nextInt(10000),
                SECURE_RANDOM.nextInt(10000),
                SECURE_RANDOM.nextInt(10000));
    }

    /** Generates a user display ID format: XXXX-XXXX-XXXX */
    public static String generateDisplayIdCandidate() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            if (i == 4 || i == 9) sb.append('-');
            else sb.append(DISPLAY_ID_CHARS.charAt(SECURE_RANDOM.nextInt(DISPLAY_ID_CHARS.length())));
        }
        return sb.toString();
    }
}