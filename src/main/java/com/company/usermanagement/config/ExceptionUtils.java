package com.company.usermanagement.config;

/**
 * Utility for detecting specific database exceptions.
 *
 * Centralizes exception detection logic to avoid duplication
 * across multiple layers (AuthService, UserService, GlobalExceptionHandler).
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
        // Utility class
    }

    /**
     * Detects if a throwable represents an email duplicate key violation.
     *
     * Traverses the exception cause chain looking for PostgreSQL-specific
     * error messages indicating a unique constraint violation on email.
     *
     * @param throwable The exception to check (may be null)
     * @return true if the exception represents an email duplicate, false otherwise
     */
    public static boolean isEmailDuplicateError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                // Check for PostgreSQL-specific constraint names and error patterns
                if (lower.contains("duplicate key value")
                        || lower.contains("users_email_unique")
                        || lower.contains("idx_users_email_lower")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}

