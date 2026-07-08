package com.company.usermanagement.service;

import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.dto.request.LoginRequest;
import com.company.usermanagement.dto.request.RegisterRequest;
import com.company.usermanagement.dto.response.AuthResponse;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.AuditAction;
import com.company.usermanagement.entity.enums.Role;
import com.company.usermanagement.exception.BadCredentialsException;
import com.company.usermanagement.exception.EmailAlreadyExistsException;
import com.company.usermanagement.mapper.UserMapper;
import com.company.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        log.info("Registration attempt for email: {}", normalizedEmail);

        User user = User.builder()
                .firstName(trim(request.getFirstName()))
                .lastName(trim(request.getLastName()))
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_USER)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailConflict(ex)) {
                throw new EmailAlreadyExistsException(normalizedEmail);
            }
            throw ex;
        }

        RefreshTokenService.TokenBundle bundle = refreshTokenService.issueTokens(savedUser);
        auditService.recordUserEvent(AuditAction.USER_REGISTERED, savedUser, savedUser.getId());

        return buildAuthResponse(bundle);
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        log.info("Login attempt for email: {}", normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        // Always perform exactly one password verification so the response
        // time does not depend on whether the account exists. Skipping the
        // (deliberately slow) Argon2 hash for unknown emails leaks account
        // existence through a timing side-channel — an attacker can enumerate
        // valid accounts by measuring latency. Unknown accounts are verified
        // against a fixed dummy hash instead.
        String encodedPassword = user != null ? user.getPassword() : dummyHash();
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), encodedPassword);

        if (user == null) {
            log.warn("Login failed for unknown email: {}", normalizedEmail);
            throw new BadCredentialsException();
        }

        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            log.warn("Login rejected for locked/disabled account: {}", normalizedEmail);
            throw new BadCredentialsException();
        }

        if (!passwordMatches) {
            user.registerFailedLoginAttempt(
                    appProperties.getAuth().getMaxFailedAttempts(),
                    appProperties.getAuth().getLockDurationMinutes());
            userRepository.save(user);
            log.warn("Login failed for email: {}", normalizedEmail);
            throw new BadCredentialsException();
        }

        user.clearLoginFailures();
        User authenticatedUser = userRepository.save(user);
        RefreshTokenService.TokenBundle bundle = refreshTokenService.issueTokens(authenticatedUser);
        auditService.recordUserEvent(AuditAction.USER_LOGGED_IN, authenticatedUser, authenticatedUser.getId());

        return buildAuthResponse(bundle);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.TokenBundle bundle = refreshTokenService.rotateRefreshToken(refreshToken);
        auditService.recordUserEvent(AuditAction.USER_REFRESHED_TOKEN, bundle.user(), bundle.user().getId());
        return buildAuthResponse(bundle);
    }

    @Transactional
    public void logout(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(BadCredentialsException::new);
        refreshTokenService.deleteAllForUser(user.getId());
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.saveAndFlush(user);
    }

    private AuthResponse buildAuthResponse(RefreshTokenService.TokenBundle bundle) {
        return AuthResponse.builder()
                .accessToken(bundle.accessToken())
                .refreshToken(bundle.refreshToken())
                .tokenType("Bearer")
                .expiresIn(bundle.accessTokenExpiresIn())
                .refreshExpiresIn(bundle.refreshTokenExpiresIn())
                .user(userMapper.toResponse(bundle.user()))
                .build();
    }

    /**
     * A valid, constant encoded password used to run a real (slow) hash
     * verification for login attempts against non-existent accounts, so the
     * response time matches that of an existing account. Computed lazily once
     * with the configured encoder so it always uses the current algorithm and
     * work factor.
     */
    private volatile String dummyHash;

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            synchronized (this) {
                hash = dummyHash;
                if (hash == null) {
                    hash = passwordEncoder.encode("timing-safe-dummy-password-value");
                    dummyHash = hash;
                }
            }
        }
        return hash;
    }

    private String normalizeEmail(String email) {
        return trim(email).toLowerCase();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isEmailConflict(Throwable throwable) {
        return com.company.usermanagement.config.ExceptionUtils.isEmailDuplicateError(throwable);
    }
}
