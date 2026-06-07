package com.company.usermanagement.testsupport;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Test-only JWT key material.
 *
 * Generated at runtime so the repository does not store any committed secrets.
 */
public final class TestJwtSecrets {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE64_SECRET = generateBase64Secret();

    private TestJwtSecrets() {
    }

    public static String base64Secret() {
        return BASE64_SECRET;
    }

    private static String generateBase64Secret() {
        byte[] keyBytes = new byte[32];
        RANDOM.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}
