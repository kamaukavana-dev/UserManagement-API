package com.company.usermanagement.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom constraint annotation for password strength validation.
 * Enforces password complexity requirements for security.
 * 
 * Requirements:
 * - Minimum 12 characters
 * - At least 1 uppercase letter
 * - At least 1 digit
 * - At least 1 special character (@$!%*?&_#)
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
@Documented
public @interface ValidPassword {
    String message() default "Invalid password";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    boolean nullable() default false;
}
