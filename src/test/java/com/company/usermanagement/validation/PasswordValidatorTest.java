package com.company.usermanagement.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordValidator.
 * Tests all password complexity requirements.
 */
@DisplayName("PasswordValidator Tests")
class PasswordValidatorTest {

    private PasswordValidator validator = new PasswordValidator();

    @Test
    @DisplayName("Should accept valid password with all requirements")
    void testValidPassword() {
        validator.initialize(null);
        assertTrue(validator.isValid("SecurePass123!", null));
    }

    @Test
    @DisplayName("Should accept another valid password format")
    void testValidPasswordAlternativeFormat() {
        validator.initialize(null);
        assertTrue(validator.isValid("MyP@ssw0rd_Test", null));
    }

    @Test
    @DisplayName("Should accept password with underscore special character")
    void testValidPasswordWithUnderscore() {
        validator.initialize(null);
        assertTrue(validator.isValid("Pass_123_word", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "short",                    // Too short (< 12 chars)
        "shortpass1!",              // Too short (11 chars)
        "nouppercase123!",          // No uppercase letter
        "NoSpecialChar123",         // No special character
        "NoNumber!Abc",             // No digit
        "NoUpAndNum!",              // No uppercase and no number
        "onlylettershere",          // Only lowercase letters
        "ONLYUPPERCASE",            // Only uppercase letters
        "123456789012",             // Only numbers (12 chars minimum met, but no letters/special)
        "!@#$%^&*_#!!",            // Only special characters
    })
    @DisplayName("Should reject passwords that don't meet complexity requirements")
    void testInvalidPasswords(String password) {
        validator.initialize(null);
        assertFalse(validator.isValid(password, null),
            "Password should be invalid: " + password);
    }

    @Test
    @DisplayName("Should reject null password")
    void testNullPassword() {
        validator.initialize(null);
        assertFalse(validator.isValid(null, null));
    }

    @Test
    @DisplayName("Should reject empty password")
    void testEmptyPassword() {
        validator.initialize(null);
        assertFalse(validator.isValid("", null));
    }

    @Test
    @DisplayName("Should accept password with minimum required length (12 chars)")
    void testMinimumLengthPassword() {
        validator.initialize(null);
        // 12 chars exactly: "Pass123!abcd"
        assertTrue(validator.isValid("Pass123!abcd", null));
    }

    @Test
    @DisplayName("Should accept password with multiple special characters")
    void testMultipleSpecialCharacters() {
        validator.initialize(null);
        assertTrue(validator.isValid("Pass123!@#$%", null));
    }

    @Test
    @DisplayName("Should accept very long password")
    void testVeryLongPassword() {
        validator.initialize(null);
        String longPassword = "Pass123!" + "a".repeat(100);
        assertTrue(validator.isValid(longPassword, null));
    }

    @Test
    @DisplayName("Should accept all allowed special characters")
    void testAllAllowedSpecialCharacters() {
        validator.initialize(null);
        // Test each allowed special character: @$!%*?&_#
        assertTrue(validator.isValid("Pass123@word", null));
        assertTrue(validator.isValid("Pass123$word", null));
        assertTrue(validator.isValid("Pass123!word", null));
        assertTrue(validator.isValid("Pass123%word", null));
        assertTrue(validator.isValid("Pass123*word", null));
        assertTrue(validator.isValid("Pass123?word", null));
        assertTrue(validator.isValid("Pass123&word", null));
        assertTrue(validator.isValid("Pass123_word", null));
        assertTrue(validator.isValid("Pass123#word", null));
    }

    @Test
    @DisplayName("Should reject password with disallowed special characters")
    void testDisallowedSpecialCharacters() {
        validator.initialize(null);
        // Test disallowed special characters
        assertFalse(validator.isValid("Pass123(word", null));
        assertFalse(validator.isValid("Pass123)word", null));
        assertFalse(validator.isValid("Pass123-word", null));
        assertFalse(validator.isValid("Pass123+word", null));
    }
}

