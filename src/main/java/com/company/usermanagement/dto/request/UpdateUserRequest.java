package com.company.usermanagement.dto.request;

import com.company.usermanagement.validation.ValidPassword;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Payload for PUT /users/{id}
 *
 * All fields are optional — only non-null fields are updated.
 * This is a partial update pattern (PATCH semantics via PUT).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @Pattern(
        regexp = "^[\\p{L}][\\p{L}\\s'-]*[\\p{L}]$|^[\\p{L}]$",
        message = "First name must contain only letters, spaces, hyphens, or apostrophes"
    )
    private String firstName;

    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    @Pattern(
        regexp = "^[\\p{L}][\\p{L}\\s'-]*[\\p{L}]$|^[\\p{L}]$",
        message = "Last name must contain only letters, spaces, hyphens, or apostrophes"
    )
    private String lastName;

    @Size(min = 12, max = 100, message = "Password must be between 12 and 100 characters")
    @ValidPassword(nullable = true)
    private String password;
}
