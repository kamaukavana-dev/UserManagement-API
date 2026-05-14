package com.company.usermanagement.security;

import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String KID_HEADER = "kid";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private JwtKeyRing keyRing;

    @PostConstruct
    void init() {
        this.keyRing = JwtKeyRing.from(appProperties.getJwt());
    }

    public String generateToken(UserDetails userDetails) {
        return buildToken(userDetails, TOKEN_TYPE_ACCESS, appProperties.getJwt().getExpirationMs(), true);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(userDetails, TOKEN_TYPE_REFRESH,
                appProperties.getJwt().getRefreshExpirationMs(), false);
    }

    public long getExpirationMs() {
        return appProperties.getJwt().getExpirationMs();
    }

    public long getRefreshExpirationMs() {
        return appProperties.getJwt().getRefreshExpirationMs();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return isAccessTokenValid(token, userDetails);
    }

    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        return isValidToken(token, userDetails, TOKEN_TYPE_ACCESS);
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        return isValidToken(token, userDetails, TOKEN_TYPE_REFRESH);
    }

    public boolean isTokenStructureValid(String token) {
        return isTokenStructureValid(token, null);
    }

    public boolean isAccessTokenStructureValid(String token) {
        return isTokenStructureValid(token, TOKEN_TYPE_ACCESS);
    }

    public boolean isRefreshTokenStructureValid(String token) {
        return isTokenStructureValid(token, TOKEN_TYPE_REFRESH);
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String extractTokenType(String token) {
        return parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
    }

    public int extractTokenVersion(String token) {
        Object tokenVersion = parseClaims(token).get(TOKEN_VERSION_CLAIM);
        if (tokenVersion instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private boolean isValidToken(String token, UserDetails userDetails, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                    && claims.getSubject() != null
                    && claims.getSubject().equals(userDetails.getUsername())
                    && !isExpired(claims)
                    && extractTokenVersionFromClaims(claims) == resolveTokenVersion(userDetails)
                    && userDetails.isEnabled()
                    && userDetails.isAccountNonLocked();
        } catch (ExpiredJwtException ex) {
            log.debug("Expired JWT rejected");
            return false;
        } catch (Exception ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isTokenStructureValid(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType == null
                    || expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (ExpiredJwtException ex) {
            log.debug("Expired JWT rejected");
            return false;
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException ex) {
            log.debug("JWT structure rejected: {}", ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.debug("JWT structure validation failed: {}", ex.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        String normalizedToken = validateAndNormalizeToken(token);
        List<SecretKey> candidateKeys = keyRing.resolveCandidateKeys(normalizedToken, objectMapper);
        RuntimeException lastFailure = null;

        for (SecretKey key : candidateKeys) {
            try {
                return Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(normalizedToken)
                        .getPayload();
            } catch (RuntimeException ex) {
                lastFailure = ex;
            }
        }

        if (lastFailure instanceof ExpiredJwtException expiredJwtException) {
            throw expiredJwtException;
        }
        if (lastFailure instanceof UnsupportedJwtException unsupportedJwtException) {
            throw unsupportedJwtException;
        }
        if (lastFailure instanceof MalformedJwtException malformedJwtException) {
            throw malformedJwtException;
        }
        if (lastFailure instanceof SignatureException signatureException) {
            throw signatureException;
        }
        throw new MalformedJwtException("Unable to parse JWT", lastFailure);
    }

    private String buildToken(UserDetails userDetails,
                              String tokenType,
                              long expirationMs,
                              boolean includeRole) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(TOKEN_TYPE_CLAIM, tokenType);
        claims.put(TOKEN_VERSION_CLAIM, resolveTokenVersion(userDetails));
        if (includeRole && !userDetails.getAuthorities().isEmpty()) {
            claims.put("role", userDetails.getAuthorities()
                    .iterator()
                    .next()
                    .getAuthority());
        }

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .header()
                .keyId(keyRing.getCurrentKeyId())
                .and()
                .id(UUID.randomUUID().toString())
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(keyRing.getCurrentKey(), Jwts.SIG.HS256)
                .compact();
    }

    private boolean isExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration != null && expiration.before(new Date());
    }

    private int extractTokenVersionFromClaims(Claims claims) {
        Object tokenVersion = claims.get(TOKEN_VERSION_CLAIM);
        if (tokenVersion instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String validateAndNormalizeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("JWT must not be blank");
        }
        String normalized = token.trim();
        if (normalized.split("\\.").length != 3) {
            throw new MalformedJwtException("Invalid JWT format");
        }
        return normalized;
    }

    private int resolveTokenVersion(UserDetails userDetails) {
        if (userDetails instanceof User user) {
            return user.getTokenVersion();
        }
        return 0;
    }

    private static final class JwtKeyRing {

        private final Map<String, SecretKey> keys;
        private final String currentKeyId;

        private JwtKeyRing(Map<String, SecretKey> keys, String currentKeyId) {
            this.keys = keys;
            this.currentKeyId = currentKeyId;
        }

        static JwtKeyRing from(AppProperties.Jwt jwtProperties) {
            Map<String, SecretKey> keys = new LinkedHashMap<>();
            SecretKey currentKey = decodeSecret(jwtProperties.getSecret());
            keys.put(jwtProperties.getKeyId(), currentKey);

            if (hasText(jwtProperties.getPreviousSecret())) {
                String previousKeyId = hasText(jwtProperties.getPreviousKeyId())
                        ? jwtProperties.getPreviousKeyId()
                        : "previous";
                if (keys.containsKey(previousKeyId)) {
                    throw new IllegalStateException("JWT key ids must be unique");
                }
                keys.put(previousKeyId, decodeSecret(jwtProperties.getPreviousSecret()));
            }

            return new JwtKeyRing(Map.copyOf(keys), jwtProperties.getKeyId());
        }

        SecretKey getCurrentKey() {
            return keys.get(currentKeyId);
        }

        String getCurrentKeyId() {
            return currentKeyId;
        }

        List<SecretKey> resolveCandidateKeys(String token, ObjectMapper objectMapper) {
            String keyId = extractKeyId(token, objectMapper);
            if (keyId != null) {
                SecretKey key = keys.get(keyId);
                if (key == null) {
                    throw new UnsupportedJwtException("Unknown JWT key id: " + keyId);
                }
                return List.of(key);
            }

            List<SecretKey> candidateKeys = new ArrayList<>();
            candidateKeys.add(keys.get(currentKeyId));
            for (Map.Entry<String, SecretKey> entry : keys.entrySet()) {
                if (!entry.getKey().equals(currentKeyId)) {
                    candidateKeys.add(entry.getValue());
                }
            }
            return candidateKeys;
        }

        private String extractKeyId(String token, ObjectMapper objectMapper) {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new MalformedJwtException("Invalid JWT format");
            }

            try {
                byte[] headerBytes = Decoders.BASE64URL.decode(parts[0]);
                JsonNode header = objectMapper.readTree(new String(headerBytes, StandardCharsets.UTF_8));
                JsonNode kid = header.get(KID_HEADER);
                if (kid == null || kid.asText().isBlank()) {
                    return null;
                }
                return kid.asText();
            } catch (IOException ex) {
                throw new MalformedJwtException("Invalid JWT header", ex);
            }
        }

        private static SecretKey decodeSecret(String secret) {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
