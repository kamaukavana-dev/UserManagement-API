package com.company.usermanagement.entity;

import com.company.usermanagement.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    @DisplayName("Should normalize fields correctly")
    void normalize_ShouldTrimAndLowerEmail() {
        User user = User.builder()
                .firstName("  John  ")
                .lastName("  Doe  ")
                .email("  John@Example.Com  ")
                .build();

        user.onCreate();

        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals("john@example.com", user.getEmail());
    }

    @Test
    @DisplayName("Should handle null and empty fields in normalize")
    void normalize_ShouldHandleNullAndEmpty() {
        User user = User.builder()
                .firstName("")
                .lastName("   ")
                .email(null)
                .build();

        user.onCreate();

        assertNull(user.getFirstName());
        assertNull(user.getLastName());
        assertNull(user.getEmail());
    }

    @Test
    @DisplayName("Should handle failed login attempts correctly")
    void registerFailedLoginAttempt_ShouldLockAccount() {
        User user = User.builder()
                .failedLoginAttempts(0)
                .build();

        user.registerFailedLoginAttempt(3, 15);
        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());

        user.registerFailedLoginAttempt(3, 15);
        user.registerFailedLoginAttempt(3, 15);
        assertEquals(3, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.isTemporarilyLocked());
    }

    @Test
    @DisplayName("Should clear login failures")
    void clearLoginFailures_ShouldResetCounters() {
        User user = User.builder()
                .failedLoginAttempts(3)
                .lockedUntil(Instant.now().plusSeconds(60))
                .build();

        user.clearLoginFailures();
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertFalse(user.isTemporarilyLocked());
    }

    @Test
    @DisplayName("Should return correct authorities")
    void getAuthorities_ShouldReturnRole() {
        User user = User.builder()
                .role(Role.ROLE_ADMIN)
                .build();

        var authorities = user.getAuthorities();
        assertEquals(1, authorities.size());
        assertEquals("ROLE_ADMIN", authorities.iterator().next().getAuthority());
    }

    @Test
    @DisplayName("UserDetails methods should return correct values")
    void userDetailsMethods_ShouldReturnCorrectValues() {
        User user = User.builder()
                .email("john@example.com")
                .password("password")
                .enabled(true)
                .accountNonLocked(true)
                .build();

        assertEquals("john@example.com", user.getUsername());
        assertEquals("password", user.getPassword());
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isCredentialsNonExpired());
    }

    @Test
    @DisplayName("Should update updatedAt on update")
    void onUpdate_ShouldUpdateTimestamp() throws InterruptedException {
        User user = User.builder().build();
        user.onCreate();
        Instant initialUpdate = user.getUpdatedAt();

        Thread.sleep(10);
        user.onUpdate();

        assertTrue(user.getUpdatedAt().isAfter(initialUpdate));
    }
}
