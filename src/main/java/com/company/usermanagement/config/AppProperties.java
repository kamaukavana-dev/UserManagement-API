package com.company.usermanagement.config;

import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed configuration binding for all {@code app.*} properties.
 */
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
@Validated
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final RateLimit rateLimit = new RateLimit();
    private final Auth auth = new Auth();
    private final Cors cors = new Cors();

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank
        private String secret;

        @NotBlank
        private String keyId = "current";

        private String previousSecret;

        private String previousKeyId = "previous";

        @Min(value = 60000, message = "Access token expiration must be >= 60s")
        private long expirationMs;

        @Min(value = 300000, message = "Refresh token expiration must be >= 5 minutes")
        private long refreshExpirationMs;
    }

    @Getter
    @Setter
    public static class RateLimit {
        @NotBlank
        private String backend = "local";

        @Min(1)
        private int capacity;

        @Min(1)
        private int refillTokens;

        @Min(1)
        private int refillSeconds;

        @Min(1)
        private int authCapacity;

        @Min(1)
        private int authRefillTokens;

        @Min(1)
        private int authRefillSeconds;

        private List<String> trustedProxyCidrs = List.of("127.0.0.1/32", "::1/128");
    }

    @Getter
    @Setter
    public static class Auth {
        @Min(1)
        private int maxFailedAttempts;

        @Min(1)
        private int lockDurationMinutes;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new java.util.ArrayList<>();
    }

    @PostConstruct
    void validate() {
        validateJwtSecret(jwt.getSecret(), "app.jwt.secret");
        if (hasText(jwt.getPreviousSecret())) {
            validateJwtSecret(jwt.getPreviousSecret(), "app.jwt.previous-secret");
        }

        if (!hasText(jwt.getKeyId())) {
            throw new IllegalStateException("app.jwt.key-id must not be blank");
        }
    }

    private void validateJwtSecret(String secret, String propertyName) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length < 32) {
                throw new IllegalStateException(
                        propertyName + " must decode to at least 32 bytes for HS256");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(propertyName + " must be valid Base64", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
