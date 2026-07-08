package com.company.usermanagement.service;

import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.dto.request.LoginRequest;
import com.company.usermanagement.dto.request.RegisterRequest;
import com.company.usermanagement.dto.response.AuthResponse;
import com.company.usermanagement.dto.response.UserResponse;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.Role;
import com.company.usermanagement.exception.BadCredentialsException;
import com.company.usermanagement.exception.EmailAlreadyExistsException;
import com.company.usermanagement.mapper.UserMapper;
import com.company.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditService auditService;

    @Mock
    private AppProperties appProperties;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    private AppProperties.Auth authProperties;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User existingUser;
    private User persistedUser;

    @BeforeEach
    void setUp() {
        authProperties = new AppProperties.Auth();
        authProperties.setMaxFailedAttempts(5);
        authProperties.setLockDurationMinutes(15);

        lenient().when(appProperties.getAuth()).thenReturn(authProperties);

        registerRequest = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("SecurePassword123")
                .build();

        loginRequest = LoginRequest.builder()
                .email("john@example.com")
                .password("SecurePassword123")
                .build();

        existingUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("$2a$12$hashedPassword")
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .role(Role.ROLE_USER)
                .enabled(true)
                .accountNonLocked(true)
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        persistedUser = User.builder()
                .id(42L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("$2a$12$hashedPassword")
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .role(Role.ROLE_USER)
                .enabled(true)
                .accountNonLocked(true)
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        lenient().when(userMapper.toResponse(any(User.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register user and return access + refresh tokens")
        void register_ShouldSucceed_WhenEmailIsNew() {
            when(passwordEncoder.encode("SecurePassword123")).thenReturn("$2a$12$hashedPassword");
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(persistedUser.getId());
                return user;
            });
            when(refreshTokenService.issueTokens(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return new RefreshTokenService.TokenBundle(
                        user,
                        "access.token",
                        "refresh.token",
                        86_400_000L,
                        604_800_000L);
            });

            AuthResponse response = authService.register(registerRequest);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access.token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh.token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getEmail()).isEqualTo("john@example.com");

            verify(passwordEncoder).encode("SecurePassword123");
            verify(userRepository).saveAndFlush(any(User.class));
            verify(refreshTokenService).issueTokens(any(User.class));
            verify(auditService).recordUserEvent(eq(
                    com.company.usermanagement.entity.enums.AuditAction.USER_REGISTERED),
                    any(User.class),
                    eq(42L));
        }

        @Test
        @DisplayName("should translate duplicate-email races into 409")
        void register_ShouldTranslateDuplicateRace_WhenConstraintTrips() {
            when(passwordEncoder.encode("SecurePassword123")).thenReturn("$2a$12$hashedPassword");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("john@example.com");

            verify(refreshTokenService, never()).issueTokens(any());
        }

        @Test
        @DisplayName("should rethrow other integrity violations")
        void register_ShouldRethrowOtherIntegrityViolations() {
            when(passwordEncoder.encode("SecurePassword123")).thenReturn("$2a$12$hashedPassword");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("other error"));

            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("should authenticate and clear failed-login counters")
        void login_ShouldSucceed_WithValidCredentials() {
            existingUser.setFailedLoginAttempts(3);
            when(userRepository.findByEmail("john@example.com"))
                    .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("SecurePassword123", existingUser.getPassword())).thenReturn(true);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(refreshTokenService.issueTokens(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return new RefreshTokenService.TokenBundle(
                        user,
                        "access.token",
                        "refresh.token",
                        86_400_000L,
                        604_800_000L);
            });

            AuthResponse response = authService.login(loginRequest);

            assertThat(response.getAccessToken()).isEqualTo("access.token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh.token");
            verify(passwordEncoder).matches("SecurePassword123", existingUser.getPassword());
            verify(userRepository).save(any(User.class));
            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.USER_LOGGED_IN),
                    any(User.class),
                    eq(1L));
        }

        @Test
        @DisplayName("should increment failed attempts and lock account on repeated failures")
        void login_ShouldLockAccount_WhenPasswordIncorrect() {
            existingUser.setFailedLoginAttempts(4);
            when(userRepository.findByEmail("john@example.com"))
                    .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("Wrong@123", existingUser.getPassword())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
                assertThat(user.getLockedUntil()).isNotNull();
                return user;
            });

            LoginRequest request = LoginRequest.builder()
                    .email("john@example.com")
                    .password("Wrong@123")
                    .build();

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userRepository).save(any(User.class));
            verify(refreshTokenService, never()).issueTokens(any());
        }

        @Test
        @DisplayName("should fail closed for disabled accounts")
        void login_ShouldReject_DisabledAccount() {
            existingUser.setEnabled(false);
            when(userRepository.findByEmail("john@example.com"))
                    .thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class);

            // Constant-time defence: a password verification still runs before the
            // enabled/locked check so a disabled account is indistinguishable by
            // timing from an active one. It must NOT touch the failed-attempt
            // counter (that path is reserved for wrong-password on active accounts).
            verify(passwordEncoder).matches(eq("SecurePassword123"), anyString());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should fail closed for locked accounts")
        void login_ShouldReject_LockedAccount() {
            existingUser.setAccountNonLocked(false);
            when(userRepository.findByEmail("john@example.com"))
                    .thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("should throw when user not found by email")
        void login_ShouldThrow_WhenUserNotFound() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("$argon2id$dummy$hash");

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("should run a hash verification for unknown emails to prevent timing enumeration")
        void login_ShouldPerformConstantTimeHash_WhenUserNotFound() {
            when(userRepository.findByEmail("john@example.com"))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("$argon2id$dummy$hash");

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class);

            // Pre-fix, login() threw immediately on Optional.empty() WITHOUT calling
            // the (deliberately slow) encoder — leaking account existence via latency.
            // The fix verifies the supplied password against a dummy hash instead.
            verify(passwordEncoder).matches(eq("SecurePassword123"), eq("$argon2id$dummy$hash"));
            verify(refreshTokenService, never()).issueTokens(any());
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("should rotate refresh token and return fresh credentials")
        void refresh_ShouldReturnFreshTokens() {
            when(refreshTokenService.rotateRefreshToken("refresh.token")).thenAnswer(invocation -> {
                return new RefreshTokenService.TokenBundle(
                        existingUser,
                        "new.access",
                        "new.refresh",
                        86_400_000L,
                        604_800_000L);
            });

            AuthResponse response = authService.refresh("refresh.token");

            assertThat(response.getAccessToken()).isEqualTo("new.access");
            assertThat(response.getRefreshToken()).isEqualTo("new.refresh");
            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.USER_REFRESHED_TOKEN),
                    eq(existingUser),
                    eq(1L));
        }
    }

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("should delete refresh tokens and rotate token version")
        void logout_ShouldDeleteRefreshTokens_AndIncrementTokenVersion() {
            existingUser.setTokenVersion(2);
            when(userRepository.findByEmail("john@example.com"))
                    .thenReturn(Optional.of(existingUser));
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            authService.logout("john@example.com");

            verify(refreshTokenService).deleteAllForUser(1L);
            verify(userRepository).saveAndFlush(any(User.class));
            assertThat(existingUser.getTokenVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw bad credentials when user does not exist")
        void logout_ShouldThrow_WhenUserNotFound() {
            when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.logout("missing@example.com"))
                    .isInstanceOf(BadCredentialsException.class);

            verify(refreshTokenService, never()).deleteAllForUser(any());
            verify(userRepository, never()).saveAndFlush(any());
        }
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
