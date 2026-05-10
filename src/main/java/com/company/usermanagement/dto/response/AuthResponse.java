package com.company.usermanagement.dto.response;

import lombok.*;

/**
 * Returned after successful login or registration.
 * Never include the password or sensitive fields here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;       // milliseconds until expiry
    private long refreshExpiresIn;
    private UserResponse user;
}
