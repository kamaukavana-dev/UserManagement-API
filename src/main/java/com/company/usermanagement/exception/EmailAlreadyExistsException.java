package com.company.usermanagement.exception;

/**
 * Thrown during registration when the email is already registered.
 * Maps to HTTP 409 Conflict in the GlobalExceptionHandler.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super(String.format("Email already registered: %s", email));
    }
}