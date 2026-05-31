package com.company.usermanagement.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExceptionClassesTest {

    @Test
    void emailAlreadyExistsException_storesMessage() {
        var ex = new EmailAlreadyExistsException("test@example.com");
        assertThat(ex.getMessage()).contains("test@example.com");
    }

    @Test
    void badCredentialsException_storesMessage() {
        var ex = new BadCredentialsException("invalid credentials");
        assertThat(ex.getMessage()).isEqualTo("invalid credentials");
    }

    @Test
    void resourceNotFoundException_withIdConstructor() {
        var ex = new ResourceNotFoundException("User", 42L);
        assertThat(ex.getMessage()).contains("User").contains("42");
    }

    @Test
    void resourceNotFoundException_withStringConstructor() {
        var ex = new ResourceNotFoundException("User not found");
        assertThat(ex.getMessage()).isEqualTo("User not found");
    }
}
