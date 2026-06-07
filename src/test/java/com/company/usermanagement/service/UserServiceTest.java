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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User user;
    private User adminUser;

    @BeforeEach
    void setUp() {
        user = User.builder()
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

        adminUser = User.builder()
                .id(2L)
                .firstName("Alice")
                .lastName("Admin")
                .email("admin@example.com")
                .password("$2a$12$hashedPassword")
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .accountNonLocked(true)
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        lenient().when(userMapper.toResponse(any(User.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
    }

    @Nested
    @DisplayName("getUserById()")
    class GetUserByIdTests {

        @Test
        @DisplayName("should return mapped user when found")
        void getUserById_ShouldReturnUser_WhenExists() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            UserResponse response = userService.getUserById(1L);

            assertThat(response.getEmail()).isEqualTo("john@example.com");
            verify(userRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user does not exist")
        void getUserById_ShouldThrow_WhenNotFound() {
            when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with id: 999"); // Added specific message check
        }
    }

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsersTests {

        @Test
        @DisplayName("should cap page size at 100 and apply requested sort")
        void getAllUsers_ShouldCapPageSize_At100() {
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(user)));

            PagedResponse<UserResponse> response = userService.getAllUsers(0, 999, "createdAt", "desc");

            assertThat(response.getContent()).hasSize(1);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).findAll(pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("should support ascending sort")
        void getAllUsers_ShouldSupportAscSort() {
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(user)));

            userService.getAllUsers(0, 20, "firstName", "asc");

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).findAll(pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort().getOrderFor("firstName").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
        }

        @Test
        @DisplayName("should reject invalid sort fields")
        void getAllUsers_ShouldRejectInvalidSortField() {
            assertThatThrownBy(() -> userService.getAllUsers(0, 20, "drop_table", "desc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid sort field: drop_table"); // Added specific message check
        }

        @Test
        @DisplayName("should reject invalid sort direction")
        void getAllUsers_ShouldRejectInvalidSortDirection() {
            assertThatThrownBy(() -> userService.getAllUsers(0, 20, "firstName", "invalid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid sort direction: invalid"); // Added specific message check
        }
    }

    @Nested
    @DisplayName("searchUsers()")
    class SearchUsersTests {

        @Test
        @DisplayName("should trim keyword and cap page size")
        void searchUsers_ShouldTrimKeyword() {
            when(userRepository.searchByKeyword(anyString(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(user)));

            PagedResponse<UserResponse> response = userService.searchUsers("  john  ", 0, 500);

            assertThat(response.getContent()).hasSize(1);
            verify(userRepository).searchByKeyword(eq("john"), any(Pageable.class));
        }

        @Test
        @DisplayName("should return all users if keyword is null or blank")
        void searchUsers_ShouldReturnAll_WhenKeywordEmpty() {
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(user)));

            userService.searchUsers(null, 0, 20);
            userService.searchUsers("   ", 0, 20);

            verify(userRepository, times(2)).findAll(any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("updateUser()")
    class UpdateUserTests {

        @Test
        @DisplayName("should update profile fields and rotate password version")
        void updateUser_ShouldUpdateAndRotatePassword_WhenProvided() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("NewSecurePassword123")).thenReturn("encoded");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Updated")
                    .lastName("User")
                    .password("NewSecurePassword123")
                    .build();

            UserResponse response = userService.updateUser(1L, request);

            assertThat(response.getFirstName()).isEqualTo("Updated");
            assertThat(response.getLastName()).isEqualTo("User");
            verify(passwordEncoder).encode("NewSecurePassword123");
            verify(refreshTokenService).revokeAllForUser(1L);
            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.PASSWORD_CHANGED),
                    any(User.class),
                    isNull());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when updating non-existent user")
        void updateUser_ShouldThrow_WhenNotFound() {
            when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.updateUser(999L, new UpdateUserRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with id: 999"); // Added specific message check
        }
    }

    @Nested
    @DisplayName("createUserByAdmin()")
    class CreateUserByAdminTests {

        @Test
        @DisplayName("should create user with given role")
        void createUserByAdmin_ShouldCreateUser_WithGivenRole() {
            when(passwordEncoder.encode("Admin@123")).thenReturn("hashed-admin");
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User created = invocation.getArgument(0);
                created.setId(10L);
                return created;
            });

            UserResponse response = userService.createUserByAdmin(
                    AdminCreateUserRequest.builder()
                            .firstName("New")
                            .lastName("Admin")
                            .email("newadmin@example.com")
                            .password("Admin@123")
                            .role(Role.ROLE_ADMIN)
                            .build());

            assertThat(response.getRole()).isEqualTo(Role.ROLE_ADMIN);
            verify(passwordEncoder).encode("Admin@123");
            verify(userRepository).saveAndFlush(any(User.class));
            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.USER_REGISTERED),
                    any(User.class),
                    isNull());
        }

        @Test
        @DisplayName("should throw EmailAlreadyExistsException for duplicate email")
        void createUserByAdmin_ShouldTranslateDuplicateRace_WhenConstraintTrips() {
            when(passwordEncoder.encode("Admin@123")).thenReturn("hashed-admin");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\"")); // More specific mock exception

            assertThatThrownBy(() -> userService.createUserByAdmin(
                    AdminCreateUserRequest.builder()
                            .firstName("New")
                            .lastName("Admin")
                            .email("newadmin@example.com")
                            .password("Admin@123")
                            .role(Role.ROLE_ADMIN)
                            .build()))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("newadmin@example.com");

            verify(refreshTokenService, never()).issueTokens(any());
        }

        @Test
        @DisplayName("should rethrow other DataIntegrityViolationExceptions")
        void createUserByAdmin_ShouldRethrowOtherIntegrityViolations() {
            when(passwordEncoder.encode("Admin@123")).thenReturn("hashed-admin");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("some other error"));

            assertThatThrownBy(() -> userService.createUserByAdmin(
                    AdminCreateUserRequest.builder()
                            .firstName("New")
                            .lastName("Admin")
                            .email("newadmin@example.com")
                            .password("Admin@123")
                            .role(Role.ROLE_ADMIN)
                            .build()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {
        @Test
        @DisplayName("should throw BadCredentialsException when user not found by email")
        void changePassword_ShouldThrow_WhenUserNotFound() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.changePassword("none@example.com", new ChangePasswordRequest()))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("should change password and revoke refresh tokens when current password matches")
        void changePassword_ShouldEncodeNewPassword_WhenCurrentPasswordMatches() {
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldSecurePassword123", user.getPassword())).thenReturn(true);
            when(passwordEncoder.encode("NewSecurePassword123")).thenReturn("encoded-new-password");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("OldSecurePassword123")
                    .newPassword("NewSecurePassword123")
                    .build();

            UserResponse response = userService.changePassword("john@example.com", request);

            assertThat(response.getEmail()).isEqualTo("john@example.com");
            verify(passwordEncoder).encode("NewSecurePassword123");
            verify(refreshTokenService).revokeAllForUser(1L);
            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.PASSWORD_CHANGED),
                    any(User.class),
                    eq(1L));
        }

        @Test
        @DisplayName("should reject incorrect current password")
        void changePassword_ShouldThrow_WhenCurrentPasswordInvalid() {
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongOldSecurePassword123", user.getPassword())).thenReturn(false);

            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("WrongOldSecurePassword123")
                    .newPassword("NewSecurePassword123")
                    .build();

            assertThatThrownBy(() -> userService.changePassword("john@example.com", request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Current password is incorrect"); // Added specific message check

            verify(userRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("updateUserRole()")
    class UpdateUserRoleTests {

        @Test
        @DisplayName("should rotate token version and revoke refresh tokens")
        void updateUserRole_ShouldRevokeTokens() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UserResponse response = userService.updateUserRole(1L, Role.ROLE_ADMIN);

            assertThat(response.getRole()).isEqualTo(Role.ROLE_ADMIN);
            verify(refreshTokenService).revokeAllForUser(1L);
            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.ROLE_CHANGED),
                    any(User.class),
                    isNull());
        }
    }

    @Nested
    @DisplayName("setUserEnabled()")
    class SetUserEnabledTests {

        @Test
        @DisplayName("should disable user and revoke refresh tokens")
        void setUserEnabled_ShouldDisableUser() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            userService.setUserEnabled(1L, false);

            verify(refreshTokenService).revokeAllForUser(1L);
            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.ACCOUNT_DISABLED),
                    any(User.class),
                    isNull());
        }

        @Test
        @DisplayName("should enable user")
        void setUserEnabled_ShouldEnableUser() {
            user.setEnabled(false); // Set user to disabled state first
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            userService.setUserEnabled(1L, true);

            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.ACCOUNT_ENABLED),
                    any(User.class),
                    isNull());
        }
    }

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUserTests {

        @Test
        @DisplayName("should soft-delete user and revoke tokens")
        void deleteUser_ShouldDisableUser_NotHardDelete() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            userService.deleteUser(1L);

            verify(refreshTokenService).revokeAllForUser(1L);
            verify(auditService).recordUserEvent(
                    eq(com.company.usermanagement.entity.enums.AuditAction.ACCOUNT_DELETED),
                    any(User.class),
                    isNull());
        }
    }

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return the current user's profile")
        void getCurrentUser_ShouldReturnUser() {
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

            UserResponse response = userService.getCurrentUser("john@example.com");

            assertThat(response.getEmail()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when current user not found")
        void getCurrentUser_ShouldThrow_WhenNotFound() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.getCurrentUser("none@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with email: none@example.com"); // Added specific message check
        }
    }

    // --- Negative Test Cases to Add ---

    @Nested
    @DisplayName("Negative Test Cases")
    class NegativeTests {

        @Test
        @DisplayName("getUserById - should throw ResourceNotFoundException for non-existent ID")
        void getUserById_NotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.getUserById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with id: 999");
        }

        @Test
        @DisplayName("getAllUsers - should throw IllegalArgumentException for invalid sort field")
        void getAllUsers_InvalidSortField() {
            assertThatThrownBy(() -> userService.getAllUsers(0, 20, "invalidField", "asc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid sort field: invalidField");
        }

        @Test
        @DisplayName("createUserByAdmin - should throw EmailAlreadyExistsException for duplicate email")
        void createUserByAdmin_DuplicateEmail() {
            User existingUser = createTestUser("existing@example.com", Role.ROLE_USER, true);
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\""));

            AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                    .firstName("Test")
                    .lastName("User")
                    .email("existing@example.com")
                    .password("Password123")
                    .role(Role.ROLE_USER)
                    .build();

            assertThatThrownBy(() -> userService.createUserByAdmin(request))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("existing@example.com");
        }

        @Test
        @DisplayName("changePassword - should throw BadCredentialsException for wrong current password")
        void changePassword_WrongCurrentPassword() {
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPassword", user.getPassword())).thenReturn(false);

            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .currentPassword("WrongPassword")
                    .newPassword("NewSecurePassword123")
                    .build();

            assertThatThrownBy(() -> userService.changePassword("john@example.com", request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Current password is incorrect");
        }

        @Test
        @DisplayName("getCurrentUser - should throw ResourceNotFoundException for unauthenticated principal")
        void getCurrentUser_UnauthenticatedPrincipal() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.getCurrentUser("unauthenticated@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with email: unauthenticated@example.com");
        }
    }

    // Helper method to simulate UserMapper behavior for tests
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
    
    // Helper method to create a base user for tests
    private User createTestUser(String email, Role role, boolean enabled) {
        return User.builder()
                .id(System.nanoTime()) // Use a unique ID for mock scenarios if needed
                .firstName("Test")
                .lastName("User")
                .email(email)
                .password("hashedpassword") 
                .role(role)
                .enabled(enabled)
                .accountNonLocked(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
