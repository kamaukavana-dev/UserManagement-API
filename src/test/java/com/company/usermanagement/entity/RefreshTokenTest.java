package com.company.usermanagement.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {

    @Test
    @DisplayName("Should update timestamps on create and update")
    void timestamps_ShouldBeUpdated() throws InterruptedException {
        RefreshToken token = new RefreshToken();
        token.onCreate();

        assertNotNull(token.getCreatedAt());
        assertNotNull(token.getUpdatedAt());
        Instant initialUpdate = token.getUpdatedAt();

        Thread.sleep(10);
        token.onUpdate();
        assertTrue(token.getUpdatedAt().isAfter(initialUpdate));
    }

    @Test
    @DisplayName("isActive should return correct status")
    void isActive_ShouldReturnCorrectStatus() {
        RefreshToken token = RefreshToken.builder()
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertTrue(token.isActive());

        token.setRevokedAt(Instant.now());
        assertFalse(token.isActive());

        token.setRevokedAt(null);
        token.setExpiresAt(Instant.now().minusSeconds(60));
        assertFalse(token.isActive());
    }
}
