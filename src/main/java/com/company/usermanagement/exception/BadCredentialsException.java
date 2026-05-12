package com.company.usermanagement.exception;

/**
 * Thrown when login credentials are invalid.
 * Maps to HTTP 401 in the GlobalExceptionHandler.
 *
 * We use our own exception (not Spring's) so the message is
 * always under our control — never leaks internal detail.
 */
public class BadCredentialsException extends RuntimeException {

    public BadCredentialsException() {
        this("Invalid email or password");
    }

    public BadCredentialsException(String message) {
        super(message);
    }
}
