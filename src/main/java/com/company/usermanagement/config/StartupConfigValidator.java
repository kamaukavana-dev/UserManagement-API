package com.company.usermanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class StartupConfigValidator implements BeanFactoryPostProcessor, PriorityOrdered, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validate();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    void validate() {
        log.info("Starting configuration validation...");

        validateStructuralConfig();
        
        // Skip external service connectivity checks in test profile to allow Testcontainers.
        boolean isTestProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (!isTestProfile) {
            validateExternalServiceConnectivity();
        } else {
            log.info("Running in test profile. Skipping external service connectivity validation.");
        }

        log.info("Configuration validation complete.");
    }

    private void validateStructuralConfig() {
        log.info("Validating structural configuration properties...");

        // JWT Secret length check
        String jwtSecret = environment.getProperty("app.jwt.secret");
        if (jwtSecret == null) {
            log.error("Critical configuration property 'app.jwt.secret' is missing!");
            throw new IllegalStateException("JWT secret is not configured.");
        }
        // For HS256, a key of at least 32 bytes (256 bits) is recommended.
        if (jwtSecret.length() < 32) {
            log.warn("JWT secret is shorter than 32 characters. For production, a strong secret of at least 32 bytes is recommended.");
        }

        // CORS Configuration check for production
        boolean isProdProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProdProfile) {
            String[] allowedOriginsArray = environment.getProperty("app.cors.allowed-origins", String[].class);
            List<String> allowedOrigins = allowedOriginsArray == null
                    ? List.of()
                    : List.of(allowedOriginsArray);
            if (allowedOrigins.isEmpty()) {
                log.error("CORS allowed origins are not configured for production. Please set 'app.cors.allowed-origins'.");
                throw new IllegalStateException("CORS allowed origins must be explicitly configured for production.");
            }
        }

        log.info("Structural configuration validation complete.");
    }

    private void validateExternalServiceConnectivity() {
        log.info("Validating external service connectivity (e.g., database)...");
        
        List<String> criticalDatabaseKeys = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password"
        );

        boolean hasError = false;
        for (String key : criticalDatabaseKeys) {
            String value = environment.getProperty(key);
            if (value == null) {
                log.error("Critical database configuration property '{}' is missing!", key);
                hasError = true;
            } else if (value.contains("${")) {
                log.error("Critical database configuration property '{}' contains an unresolved placeholder: {}", key, value);
                hasError = true;
            }
        }

        if (hasError) {
            throw new IllegalStateException("Application startup failed due to invalid or unresolved database configuration. " +
                    "Check your environment variables and property files.");
        }
        
        // Redis configuration validation could be added here if 'app.redis.host' etc. are critical and need runtime checks.
        // For now, relying on Spring Boot's Redis starter to handle basic connectivity issues.

        log.info("External service connectivity validation complete.");
    }
}