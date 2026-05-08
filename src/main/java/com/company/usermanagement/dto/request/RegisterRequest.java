package com.company.usermanagement.dto.request;


import com.company.usermanagement.validation.ValidPassword;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Payload for POST /auth/register
 *
 * Validation annotations are enforced by @Valid in the controller.
 * The service trusts that if it receives this object, it's already valid.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

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

    @ValidPassword
    @NotBlank(message = "Password is required")
    private String password;
}
