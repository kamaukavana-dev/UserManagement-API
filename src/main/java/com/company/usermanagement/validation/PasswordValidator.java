package com.company.usermanagement.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates password strength requirements.
 *
 * Requirements: Min 12 chars, min 1 uppercase, min 1 number, min 1 special char.
 *
 * Why these requirements:
 * - 12 chars: Provides ~80-bit entropy with full charset
 * - Uppercase: Prevents lowercase-only dictionary attacks
 * - Number: Adds numeric entropy
 * - Special char: Exponentially increases keyspace
 *
 * Reference: OWASP Password Storage Cheat Sheet
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final String PASSWORD_PATTERN =
        "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#])(?=.{12,}).*$";

    private static final String ERROR_MESSAGE =
        "Password must be at least 12 characters and contain: uppercase letter, number, and special character (@$!%*?&_#)";
    private boolean nullable;

    @Override
    public void initialize(ValidPassword constraint) {
        this.nullable = constraint != null && constraint.nullable();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return nullable;
        }

        if (value.isEmpty()) {
            return false;
        }

        boolean isValid = value.matches(PASSWORD_PATTERN);

        if (!isValid && context != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(ERROR_MESSAGE)
                   .addConstraintViolation();
        }

        return isValid;
    }
}
