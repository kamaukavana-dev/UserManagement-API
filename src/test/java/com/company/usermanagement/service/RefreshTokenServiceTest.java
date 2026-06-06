package com.company.usermanagement.service;

import com.company.usermanagement.entity.RefreshToken;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.exception.BadCredentialsException;
import com.company.usermanagement.repository.RefreshTokenRepository;
import com.company.usermanagement.repository.UserRepository;
import com.company.usermanagement.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;
    private String rawToken = "valid.refresh.token";

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();
    }

    @Test
    void issueTokens_ShouldReturnTokenBundleAndPersist() {
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);
        when(jwtService.getRefreshExpirationMs()).thenReturn(86400000L);

        RefreshTokenService.TokenBundle bundle = refreshTokenService.issueTokens(user);

        assertThat(bundle.accessToken()).isEqualTo("access-token");
        assertThat(bundle.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotateRefreshToken_WhenValid_ShouldRevokeOldAndIssueNew() {
        when(jwtService.isRefreshTokenStructureValid(rawToken)).thenReturn(true);
        when(jwtService.extractEmail(rawToken)).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.isRefreshTokenValid(rawToken, user)).thenReturn(true);
        
        RefreshToken stored = RefreshToken.builder().id(1L).user(user).build();
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(stored));

        when(jwtService.generateToken(user)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh-token");

        RefreshTokenService.TokenBundle bundle = refreshTokenService.rotateRefreshToken(rawToken);

        assertThat(bundle.accessToken()).isEqualTo("new-access-token");
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotateRefreshToken_WhenInvalidStructure_ShouldThrowException() {
        when(jwtService.isRefreshTokenStructureValid(rawToken)).thenReturn(false);

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotateRefreshToken_WhenUserNotFound_ShouldThrowException() {
        when(jwtService.isRefreshTokenStructureValid(rawToken)).thenReturn(true);
        when(jwtService.extractEmail(rawToken)).thenReturn("none@example.com");
        when(userRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotateRefreshToken_WhenInvalidToken_ShouldThrowException() {
        when(jwtService.isRefreshTokenStructureValid(rawToken)).thenReturn(true);
        when(jwtService.extractEmail(rawToken)).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.isRefreshTokenValid(rawToken, user)).thenReturn(false);

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotateRefreshToken_WhenTokenNotFoundOrRevoked_ShouldThrowException() {
        when(jwtService.isRefreshTokenStructureValid(rawToken)).thenReturn(true);
        when(jwtService.extractEmail(rawToken)).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.isRefreshTokenValid(rawToken, user)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void revoke_ShouldSetRevokedAt() {
        RefreshToken stored = RefreshToken.builder().id(1L).build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        refreshTokenService.revoke(rawToken);

        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void revokeAllForUser_ShouldCallRepository() {
        refreshTokenService.revokeAllForUser(1L);
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(1L), any(Instant.class));
    }
}
