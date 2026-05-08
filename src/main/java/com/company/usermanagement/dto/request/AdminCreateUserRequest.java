package com.company.usermanagement.dto.request;

import com.company.usermanagement.entity.enums.Role;
import com.company.usermanagement.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload for ADMIN-only user creation.
 *
 * Unlike public registration, this request allows the caller to
 * choose the role explicitly. The endpoint itself is protected by
 * @PreAuthorize("hasRole('ADMIN')").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCreateUserRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @Pattern(
        regexp = "^[\\p{L}][\\p{L}\\s'-]*[\\p{L}]$|^[\\p{L}]$",
        message = "First name must contain only letters, spaces, hyphens, or apostrophes"
    )
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    @Pattern(
        regexp = "^[\\p{L}][\\p{L}\\s'-]*[\\p{L}]$|^[\\p{L}]$",
        message = "Last name must contain only letters, spaces, hyphens, or apostrophes"
    )
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 100, message = "Password must be between 12 and 100 characters")
    @ValidPassword
    private String password;

    @NotNull(message = "Role is required")
    private Role role;
}
