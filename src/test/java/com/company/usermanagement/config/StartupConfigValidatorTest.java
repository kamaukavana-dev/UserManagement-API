package com.company.usermanagement.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartupConfigValidator Unit Tests")
class StartupConfigValidatorTest {

    @Mock
    private Environment environment;

    @InjectMocks
    private StartupConfigValidator validator;

    @Test
    @DisplayName("Should skip external connectivity checks in test profile")
    void validate_ShouldSkipInTestProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        when(environment.getProperty("app.jwt.secret")).thenReturn("test-secret-32-characters-minimum-123456");

        assertDoesNotThrow(() -> validator.validate());
        verify(environment, never()).getProperty("spring.datasource.url");
    }

    @Test
    @DisplayName("Should throw exception if JWT secret is missing in prod profile")
    void validate_ShouldThrow_WhenProdJwtSecretMissing() {
        when(environment.getProperty("app.jwt.secret")).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("JWT secret is not configured"));
    }

    @Test
    @DisplayName("Should throw exception if critical property is missing")
    void validate_ShouldThrow_WhenCriticalPropertyMissing() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(environment.getProperty("app.jwt.secret")).thenReturn("test-secret-32-characters-minimum-123456");
        when(environment.getProperty("spring.datasource.url")).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("Application startup failed due to invalid or unresolved database configuration"));
    }

    @Test
    @DisplayName("Should throw exception if property contains placeholder")
    void validate_ShouldThrow_WhenPlaceholderPresent() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(environment.getProperty("app.jwt.secret")).thenReturn("test-secret-32-characters-minimum-123456");
        when(environment.getProperty("spring.datasource.url")).thenReturn("jdbc:postgresql://${DB_HOST}:5432/db");
        // Mock others to be fine
        when(environment.getProperty("spring.datasource.username")).thenReturn("user");
        when(environment.getProperty("spring.datasource.password")).thenReturn("pass");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.validate());
        assertTrue(ex.getMessage().contains("Application startup failed due to invalid or unresolved database configuration"));
    }

    @Test
    @DisplayName("Should succeed if all properties are resolved")
    void validate_ShouldSucceed_WhenAllGood() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(environment.getProperty(anyString())).thenReturn("valid_value");

        assertDoesNotThrow(() -> validator.validate());
    }
}
