package com.company.usermanagement.dto.request;

import com.company.usermanagement.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload for changing the authenticated user's password.
 *
 * The current password is required so users cannot change passwords
 * without proving they know the existing one.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @ValidPassword
    @NotBlank(message = "New password is required")
    private String newPassword;
}
