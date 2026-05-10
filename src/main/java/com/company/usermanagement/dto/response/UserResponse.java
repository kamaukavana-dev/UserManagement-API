package com.company.usermanagement.dto.response;

import com.company.usermanagement.entity.enums.Role;
import lombok.*;

import java.time.Instant;

/**
 * Safe public representation of a User entity.
 *
 * This is what leaves the service layer and reaches the HTTP response.
 * The raw User entity (with password hash) NEVER leaves the service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
