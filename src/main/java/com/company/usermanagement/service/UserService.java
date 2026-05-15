package com.company.usermanagement.service;

import com.company.usermanagement.dto.request.AdminCreateUserRequest;
import com.company.usermanagement.dto.request.ChangePasswordRequest;
import com.company.usermanagement.dto.request.UpdateUserRequest;
import com.company.usermanagement.dto.response.PagedResponse;
import com.company.usermanagement.dto.response.UserResponse;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.AuditAction;
import com.company.usermanagement.entity.enums.Role;
import com.company.usermanagement.exception.BadCredentialsException;
import com.company.usermanagement.exception.EmailAlreadyExistsException;
import com.company.usermanagement.exception.ResourceNotFoundException;
import com.company.usermanagement.mapper.UserMapper;
import com.company.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "createdAt", "updatedAt", "firstName", "lastName", "email", "role", "enabled");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getAllUsers(
            int page, int size, String sortBy, String sortDir) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                buildSort(sortBy, sortDir));

        Page<User> userPage = userRepository.findAll(pageable);
        List<UserResponse> content = userPage.getContent()
                .stream()
                .map(userMapper::toResponse)
                .toList();

        return PagedResponse.of(userPage, content);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsers(
            String keyword, int page, int size) {

        if (keyword == null || keyword.isBlank()) {
            return getAllUsers(page, size, "createdAt", "desc");
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());

        Page<User> userPage = userRepository.searchByKeyword(keyword.trim(), pageable);
        List<UserResponse> content = userPage.getContent()
                .stream()
                .map(userMapper::toResponse)
                .toList();

        return PagedResponse.of(userPage, content);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsersByRole(Role role, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());

        Page<User> userPage = userRepository.findAllByRole(role, pageable);
        List<UserResponse> content = userPage.getContent()
                .stream()
                .map(userMapper::toResponse)
                .toList();

        return PagedResponse.of(userPage, content);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (request.getFirstName() != null) {
            user.setFirstName(trim(request.getFirstName()));
        }
        if (request.getLastName() != null) {
            user.setLastName(trim(request.getLastName()));
        }
        if (request.getPassword() != null) {
            rotatePassword(user, request.getPassword());
        }

        User updatedUser = userRepository.saveAndFlush(user);
        if (request.getPassword() != null) {
            refreshTokenService.revokeAllForUser(updatedUser.getId());
            auditService.recordUserEvent(AuditAction.PASSWORD_CHANGED, updatedUser, null);
        }
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public UserResponse createUserByAdmin(AdminCreateUserRequest request) {
        User user = buildUser(
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                request.getPassword(),
                request.getRole());

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailConflict(ex)) {
                throw new EmailAlreadyExistsException(normalizeEmail(request.getEmail()));
            }
            throw ex;
        }

        auditService.recordUserEvent(AuditAction.USER_REGISTERED, savedUser, null);
        return userMapper.toResponse(savedUser);
    }

    @Transactional
    public UserResponse changePassword(
            String email,
            ChangePasswordRequest request) {

        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(BadCredentialsException::new);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        rotatePassword(user, request.getNewPassword());
        User updatedUser = userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllForUser(updatedUser.getId());
        auditService.recordUserEvent(AuditAction.PASSWORD_CHANGED, updatedUser, updatedUser.getId());
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        user.setEnabled(false);
        user.setAccountNonLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllForUser(id);
        auditService.recordUserEvent(AuditAction.ACCOUNT_DELETED, user, null);
    }

    @Transactional
    public UserResponse updateUserRole(Long id, Role newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        user.setRole(newRole);
        user.setTokenVersion(user.getTokenVersion() + 1);
        User updatedUser = userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllForUser(id);
        auditService.recordUserEvent(AuditAction.ROLE_CHANGED, updatedUser, null);
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public void setUserEnabled(Long id, boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        user.setEnabled(enabled);
        user.setAccountNonLocked(enabled);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllForUser(id);
        auditService.recordUserEvent(
                enabled ? AuditAction.ACCOUNT_ENABLED : AuditAction.ACCOUNT_DISABLED,
                user,
                null);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + email));
        return userMapper.toResponse(user);
    }

    private User buildUser(
            String firstName,
            String lastName,
            String email,
            String rawPassword,
            Role role) {

        return User.builder()
                .firstName(trim(firstName))
                .lastName(trim(lastName))
                .email(normalizeEmail(email))
                .password(passwordEncoder.encode(rawPassword))
                .role(role != null ? role : Role.ROLE_USER)
                .enabled(true)
                .accountNonLocked(true)
                .build();
    }

    private void rotatePassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = normalizeSortField(sortBy);
        Sort.Direction direction;
        if ("asc".equalsIgnoreCase(sortDir)) {
            direction = Sort.Direction.ASC;
        } else if ("desc".equalsIgnoreCase(sortDir)) {
            direction = Sort.Direction.DESC;
        } else {
            throw new IllegalArgumentException("Invalid sort direction: " + sortDir);
        }
        return direction.isAscending()
                ? Sort.by(field).ascending()
                : Sort.by(field).descending();
    }

    private String normalizeSortField(String sortBy) {
        String normalized = trim(sortBy);
        if (!SORTABLE_FIELDS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid sort field: " + sortBy);
        }
        return normalized;
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
