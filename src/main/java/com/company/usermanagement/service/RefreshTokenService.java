package com.company.usermanagement.service;

import com.company.usermanagement.entity.RefreshToken;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.exception.BadCredentialsException;
import com.company.usermanagement.repository.RefreshTokenRepository;
import com.company.usermanagement.repository.UserRepository;
import com.company.usermanagement.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public TokenBundle issueTokens(User user) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        persistRefreshToken(user, refreshToken);
        return new TokenBundle(
                user,
                accessToken,
                refreshToken,
                jwtService.getExpirationMs(),
                jwtService.getRefreshExpirationMs());
    }

    @Transactional
    public TokenBundle rotateRefreshToken(String refreshToken) {
        String token = normalizeToken(refreshToken);
        if (!jwtService.isRefreshTokenStructureValid(token)) {
            throw new BadCredentialsException();
        }

        String email = jwtService.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(BadCredentialsException::new);

        if (!jwtService.isRefreshTokenValid(token, user)) {
            throw new BadCredentialsException();
        }

        String tokenHash = hash(token);
        RefreshToken stored = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(tokenHash, Instant.now())
                .orElseThrow(BadCredentialsException::new);

        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        return issueTokens(user);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
    }

    @Transactional
    public void revoke(String refreshToken) {
        String token = normalizeToken(refreshToken);
        String tokenHash = hash(token);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(existing -> {
            existing.setRevokedAt(Instant.now());
            refreshTokenRepository.save(existing);
        });
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    private void persistRefreshToken(User user, String refreshToken) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(refreshToken))
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .build();
        refreshTokenRepository.save(token);
    }

    private String normalizeToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException();
        }
        return refreshToken.trim();
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record TokenBundle(
            User user,
            String accessToken,
            String refreshToken,
            long accessTokenExpiresIn,
            long refreshTokenExpiresIn) {
    }
}
