package com.company.usermanagement.controller;

import com.company.usermanagement.dto.request.AdminCreateUserRequest;
import com.company.usermanagement.dto.request.ChangePasswordRequest;
import com.company.usermanagement.dto.request.UpdateUserRequest;
import com.company.usermanagement.dto.response.PagedResponse;
import com.company.usermanagement.dto.response.UserResponse;
import com.company.usermanagement.entity.enums.Role;
import com.company.usermanagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Handles all user management endpoints.
 * All endpoints require authentication (enforced in SecurityConfig).
 * Some endpoints additionally require ADMIN role (@PreAuthorize).
 *
 * Base URL: /api/v1/users
 *
 * @SecurityRequirement links this controller to the JWT bearer scheme
 * in the Swagger UI — shows the "Authorize" lock icon.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "CRUD operations for user accounts")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class UserController {

    private final UserService userService;

    // ─── Profile (Self) ───────────────────────────────────────────────────────

    /**
     * GET /api/v1/users/me
     *
     * Returns the currently authenticated user's own profile.
     *
     * @AuthenticationPrincipal injects the UserDetails object that was
     * set by JwtAuthenticationFilter — the currently logged-in user.
     * No ID in the URL — prevents users from fishing for other profiles.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("GET /users/me — user: {}", userDetails.getUsername());
        UserResponse response = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/users/me
     *
     * Authenticated users can update their own profile only.
     * They cannot change their own role — only ADMIN can do that.
     */
    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateUserRequest request) {

        log.info("PUT /users/me — user: {}", userDetails.getUsername());

        // Resolve email → ID, then delegate to service
        UserResponse current = userService.getCurrentUser(userDetails.getUsername());
        UserResponse updated = userService.updateUser(current.getId(), request);
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/v1/users/me/password
     *
     * Authenticated users change their own password by providing the
     * current password plus the new password.
     */
    @PutMapping("/me/password")
    @Operation(summary = "Change current user password")
    public ResponseEntity<UserResponse> changeCurrentPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {

        log.info("PUT /users/me/password — user: {}", userDetails.getUsername());
        UserResponse updated = userService.changePassword(
                userDetails.getUsername(), request);
        return ResponseEntity.ok(updated);
    }

    // ─── Admin: Read Operations ───────────────────────────────────────────────

    /**
     * GET /api/v1/users
     *
     * Paginated list of all users — ADMIN only.
     *
     * Query params:
     *   ?page=0&size=20&sortBy=createdAt&sortDir=desc
     *
     * @PreAuthorize is evaluated BEFORE the method executes.
     * If the user doesn't have ROLE_ADMIN, Spring Security throws
     * AccessDeniedException → caught by GlobalExceptionHandler → 403.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users (ADMIN only)")
    public ResponseEntity<PagedResponse<UserResponse>> getAllUsers(
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0")
            @Min(0) int page,

            @Parameter(description = "Number of records per page (max 100)")
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size,

            @Parameter(description = "Field to sort by")
            @RequestParam(defaultValue = "createdAt")
            @Pattern(regexp = "^(createdAt|updatedAt|firstName|lastName|email|role|enabled)$")
            String sortBy,

            @Parameter(description = "Sort direction: asc or desc")
            @RequestParam(defaultValue = "desc")
            @Pattern(regexp = "(?i)^(asc|desc)$")
            String sortDir) {

        log.debug("GET /users — page={}, size={}", page, size);
        return ResponseEntity.ok(
                userService.getAllUsers(page, size, sortBy, sortDir));
    }

    /**
     * GET /api/v1/users/search?keyword=john
     *
     * Search users across name and email — ADMIN only.
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search users by keyword (ADMIN only)")
    public ResponseEntity<PagedResponse<UserResponse>> searchUsers(
            @RequestParam
            @NotBlank
            @Size(max = 100)
            String keyword,
            @RequestParam(defaultValue = "0")
            @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size) {

        log.debug("GET /users/search — keyword: {}", keyword);
        return ResponseEntity.ok(
                userService.searchUsers(keyword, page, size));
    }

    /**
     * GET /api/v1/users/{id}
     *
     * ADMIN can fetch any user by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID (ADMIN only)")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id) {

        log.debug("GET /users/{}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * GET /api/v1/users/role/{role}
     *
     * Filter users by role — ADMIN only.
     */
    @GetMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get users by role (ADMIN only)")
    public ResponseEntity<PagedResponse<UserResponse>> getUsersByRole(
            @PathVariable Role role,
            @RequestParam(defaultValue = "0")
            @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size) {

        log.debug("GET /users/role/{}", role);
        return ResponseEntity.ok(
                userService.getUsersByRole(role, page, size));
    }

    // ─── Admin: Write Operations ──────────────────────────────────────────────

    /**
     * POST /api/v1/users
     *
     * ADMIN can create a user and explicitly assign the role.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create user (ADMIN only)")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody AdminCreateUserRequest request) {

        log.info("POST /users — admin creating user: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUserByAdmin(request));
    }

    /**
     * PUT /api/v1/users/{id}
     *
     * ADMIN can update any user's profile.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user by ID (ADMIN only)")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {

        log.info("PUT /users/{}", id);
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * PATCH /api/v1/users/{id}/role
     *
     * ADMIN updates a user's role.
     * PATCH is correct here — we're updating one specific field,
     * not replacing the entire resource (that would be PUT).
     */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user role (ADMIN only)")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role role) {

        log.info("PATCH /users/{}/role — new role: {}", id, role);
        return ResponseEntity.ok(userService.updateUserRole(id, role));
    }

    /**
     * PATCH /api/v1/users/{id}/status
     *
     * ADMIN enables or disables a user account.
     * Returns 204 No Content — the operation succeeded but there's
     * nothing meaningful to return.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable or disable user account (ADMIN only)")
    public ResponseEntity<Void> setUserEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled) {

        log.info("PATCH /users/{}/status — enabled: {}", id, enabled);
        userService.setUserEnabled(id, enabled);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/users/{id}
     *
     * Soft-delete (disables account). Returns 204 No Content.
     * We never hard-delete users — see UserService.deleteUser() for reasoning.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft-delete user by ID (ADMIN only)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {

        log.info("DELETE /users/{}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
