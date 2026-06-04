package com.company.usermanagement.security;

import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date; // Import Date

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private static final String ISSUER = "auth-server";
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 15 * 60 * 1000L; // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private JwtService jwtService;
    private AppProperties appProperties;
    private User user;

    private String secret;
    private String keyId;

    // KeyRing is an inner class of JwtService and is initialized within jwtService.init()
    // This test builds the signing key from the configured base64 secret when needed.
    @BeforeEach
    void setUp() {
        secret = base64("0123456789abcdef0123456789abcdef"); // 32 bytes secret key
        keyId = "current";

        appProperties = new AppProperties();
        appProperties.getJwt().setSecret(secret);
        appProperties.getJwt().setKeyId(keyId);
        appProperties.getJwt().setExpirationMs(ACCESS_TOKEN_EXPIRATION_MS);
        appProperties.getJwt().setRefreshExpirationMs(REFRESH_TOKEN_EXPIRATION_MS);

        jwtService = new JwtService(appProperties, new ObjectMapper());
        jwtService.init(); // Initialize keyRing and keys within jwtService

        user = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("generateToken should create a valid access token")
    void generateToken_ShouldCreateValidAccessToken() {
        String token = jwtService.generateToken(user);
        assertThat(token).isNotNull();
        assertThat(token).contains("."); // Basic JWT structure check
    }

    @Test
    @DisplayName("generateRefreshToken should create a valid refresh token")
    void generateRefreshToken_ShouldCreateValidRefreshToken() {
        String token = jwtService.generateRefreshToken(user);
        assertThat(token).isNotNull();
        assertThat(token).contains(".");
    }

    @Test
    @DisplayName("extractEmail should return the subject from the token")
    void extractEmail_ShouldReturnSubject() {
        String token = jwtService.generateToken(user);
        assertThat(jwtService.extractEmail(token)).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("isAccessTokenValid should return true for valid tokens")
    void isAccessTokenValid_ShouldReturnTrue_WhenValid() {
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isAccessTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("isAccessTokenValid should return false for expired tokens")
    void isAccessTokenValid_ShouldReturnFalse_WhenExpired() {
        // Manually create an expired token
        String expiredToken = Jwts.builder()
                .header().keyId(keyId).and()
                .subject(user.getEmail())
                .issuedAt(Date.from(Instant.now().minus(ACCESS_TOKEN_EXPIRATION_MS + 1000, ChronoUnit.MILLIS))) // Expired
                .expiration(Date.from(Instant.now().minusMillis(1000))) // Convert Instant to Date
                .signWith(currentKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.isAccessTokenValid(expiredToken, user)).isFalse();
    }

    @Test
    @DisplayName("isRefreshTokenValid should return true for valid tokens")
    void isRefreshTokenValid_ShouldReturnTrue_WhenValid() {
        String token = jwtService.generateRefreshToken(user);
        assertThat(jwtService.isRefreshTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("isRefreshTokenValid should return false for expired tokens")
    void isRefreshTokenValid_ShouldReturnFalse_WhenExpired() {
        // Manually create an expired token
        String expiredToken = Jwts.builder()
                .header().keyId(keyId).and()
                .subject(user.getEmail())
                .issuedAt(Date.from(Instant.now().minus(REFRESH_TOKEN_EXPIRATION_MS + 1000, ChronoUnit.MILLIS))) // Expired
                .expiration(Date.from(Instant.now().minusMillis(1000))) // Convert Instant to Date
                .signWith(currentKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.isRefreshTokenValid(expiredToken, user)).isFalse();
    }

    @Test
    @DisplayName("should reject tokens with missing subject")
    void isValidToken_ShouldReturnFalse_WhenSubjectMissing() {
        String token = Jwts.builder()
                .header().keyId("current").and()
                .issuedAt(Date.from(Instant.now())) // Convert Instant to Date
                .expiration(Date.from(Instant.now().plusMillis(ACCESS_TOKEN_EXPIRATION_MS))) // Convert Instant to Date
                .signWith(currentKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.isAccessTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("should reject tokens with wrong subject")
    void isValidToken_ShouldReturnFalse_WhenSubjectMismatch() {
        String token = jwtService.generateToken(user);
        User otherUser = User.builder().email("other@example.com").build();

        assertThat(jwtService.isAccessTokenValid(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("should reject tokens with wrong type")
    void isValidToken_ShouldReturnFalse_WhenTypeMismatch() {
        String token = jwtService.generateRefreshToken(user);
        assertThat(jwtService.isAccessTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("should reject tokens with wrong version")
    void isValidToken_ShouldReturnFalse_WhenVersionMismatch() {
        String token = jwtService.generateToken(user);
        user.setTokenVersion(1); // Different version

        assertThat(jwtService.isAccessTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("should reject tokens for locked users")
    void isValidToken_ShouldReturnFalse_WhenUserLocked() {
        String token = jwtService.generateToken(user);
        user.setAccountNonLocked(false); // Locked

        assertThat(jwtService.isAccessTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("isTokenStructureValid should handle various invalid tokens")
    void isTokenStructureValid_ShouldHandleInvalidTokens() {
        assertThat(jwtService.isTokenStructureValid(null)).isFalse();
        assertThat(jwtService.isTokenStructureValid("")).isFalse();
        assertThat(jwtService.isTokenStructureValid("not.a.jwt")).isFalse();
        assertThat(jwtService.isTokenStructureValid("a.b.c")).isFalse(); // Malformed JWT
    }

    @Test
    @DisplayName("should handle token rotation with previous key")
    void parseClaims_ShouldSupportPreviousKey() {
        AppProperties properties = new AppProperties();
        String currentSecretBase64 = base64("0123456789abcdef0123456789abcdef");
        String previousSecretBase64 = base64("abcdef0123456789abcdef0123456789");
        
        properties.getJwt().setSecret(currentSecretBase64);
        properties.getJwt().setKeyId("new-key");
        properties.getJwt().setPreviousSecret(previousSecretBase64);
        properties.getJwt().setPreviousKeyId("old-key");
        
        JwtService rotatedService = new JwtService(properties, new ObjectMapper());
        rotatedService.init();

        // Generate token with OLD key manually to simulate a token issued before rotation
        SecretKey oldKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(previousSecretBase64));
        String oldToken = Jwts.builder()
                .header().keyId("old-key").and()
                .subject(user.getEmail())
                .issuedAt(Date.from(Instant.now())) // Convert Instant to Date
                .expiration(Date.from(Instant.now().plusMillis(ACCESS_TOKEN_EXPIRATION_MS))) // Convert Instant to Date
                .signWith(oldKey, Jwts.SIG.HS256)
                .compact();

        assertThat(rotatedService.extractEmail(oldToken)).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("should throw error if key IDs are not unique during init")
    void init_ShouldThrowException_WhenKeyIdsNotUnique() {
        AppProperties properties = new AppProperties();
        String currentSecretBase64 = base64("0123456789abcdef0123456789abcdef");
        properties.getJwt().setSecret(currentSecretBase64);
        properties.getJwt().setKeyId("same-id");
        properties.getJwt().setPreviousSecret(base64("abcdef0123456789abcdef0123456789"));
        properties.getJwt().setPreviousKeyId("same-id");

        JwtService failingService = new JwtService(properties, new ObjectMapper());
        assertThrows(IllegalStateException.class, failingService::init);
    }

    private SecretKey currentKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
