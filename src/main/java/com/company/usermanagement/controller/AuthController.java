package com.company.usermanagement.controller;

import com.company.usermanagement.dto.request.LoginRequest;
import com.company.usermanagement.dto.request.RegisterRequest;
import com.company.usermanagement.dto.request.RefreshTokenRequest;
import com.company.usermanagement.dto.response.AuthResponse;
import com.company.usermanagement.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Handles public authentication endpoints.
 * No authentication required to reach these endpoints.
 *
 * Base URL: /api/v1/auth (context-path /api/v1 set in application.yml)
 *
 * Why @RequestMapping at class level?
 * Groups all related endpoints under one prefix.
 * If we ever rename the path, one change covers all methods.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Public endpoints for registration and login")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/register
     *
     * @Valid triggers Jakarta Bean Validation on RegisterRequest.
     * If validation fails, MethodArgumentNotValidException is thrown
     * and caught by GlobalExceptionHandler — controller never sees it.
     *
     * HTTP 201 Created — a new resource was created (the user account).
     * Not 200 OK — that's for updates or reads, not resource creation.
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account and returns a JWT token"
    )
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        log.info("POST /auth/register — email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login

     * HTTP 200 OK — no new resource created, just returning a token.
     */
    @PostMapping("/login")
    @Operation(
        summary = "Login with email and password",
        description = "Authenticates a user and returns a JWT access token"
    )
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("POST /auth/login — email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Rotate refresh token",
        description = "Validates a server-stored refresh token and returns a new access token"
    )
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Logout authenticated user",
            description = "Revokes refresh tokens and rotates token version"
    )
    public void logout(Authentication authentication) {
        authService.logout(authentication.getName());
    }
}
