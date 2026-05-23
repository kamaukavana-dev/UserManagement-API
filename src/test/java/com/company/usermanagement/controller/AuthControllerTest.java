package com.company.usermanagement.controller;

import com.company.usermanagement.dto.request.LoginRequest;
import com.company.usermanagement.dto.request.RefreshTokenRequest;
import com.company.usermanagement.dto.request.RegisterRequest;
import com.company.usermanagement.dto.response.AuthResponse;
import com.company.usermanagement.dto.response.UserResponse;
import com.company.usermanagement.entity.enums.Role;
import com.company.usermanagement.exception.BadCredentialsException;
import com.company.usermanagement.exception.EmailAlreadyExistsException;
import com.company.usermanagement.exception.GlobalExceptionHandler;
import com.company.usermanagement.service.AuthService;
import com.company.usermanagement.testsupport.TestJwtSecrets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.company.usermanagement.security.JwtAuthenticationFilter;
import com.company.usermanagement.security.RateLimitFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController.
 *
 * @WebMvcTest loads ONLY the web layer — no full Spring context,
 * no database, no service implementations.
 * It's fast and focused: controller + filters + exception handlers only.
 *
 * @MockitoBean replaces AuthService with a Mockito mock —
 * we control exactly what the service returns.
 *
 * @Import(GlobalExceptionHandler.class) ensures our custom
 * error responses are tested, not Spring's default error page.
 *
 * Difference from Integration tests:
 * - This: tests controller logic, validation, HTTP status codes
 * - Integration: tests the full stack with a real DB
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitFilter.class}
        ),
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, com.company.usermanagement.config.TestConfig.class})
@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

    @DynamicPropertySource
    static void registerSecrets(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecrets::base64Secret);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private AuthResponse mockAuthResponse;
    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;

    @BeforeEach
    void setUp() {
        UserResponse mockUser = UserResponse.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .role(Role.ROLE_USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        mockAuthResponse = AuthResponse.builder()
                .accessToken("eyJhbGciOiJIUzI1NiJ9.mock.token")
                .refreshToken("eyJhbGciOiJIUzI1NiJ9.mock.refresh")
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .refreshExpiresIn(604800000L)
                .user(mockUser)
                .build();

        validRegisterRequest = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("SecurePassword123!")
                .build();

        validLoginRequest = LoginRequest.builder()
                .email("john@example.com")
                .password("SecurePassword123!")
                .build();
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("Should return 200 with rotated tokens")
        void refresh_ShouldReturn200_WithAuthResponse() throws Exception {
            when(authService.refresh(any(String.class))).thenReturn(mockAuthResponse);

            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    RefreshTokenRequest.builder()
                                            .refreshToken("refresh.token")
                                            .build())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.user.password").doesNotExist());

            verify(authService).refresh(any(String.class));
        }
    }

    // ─── Register Tests ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Should return 201 with token when registration succeeds")
        void register_ShouldReturn201_WithAuthResponse() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenReturn(mockAuthResponse);

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(86400000))
                    .andExpect(jsonPath("$.user.email").value("john@example.com"))
                    .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
                    // SECURITY: password must NEVER appear in the response
                    .andExpect(jsonPath("$.user.password").doesNotExist());

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("Should return 409 when email already exists")
        void register_ShouldReturn409_WhenEmailTaken() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new EmailAlreadyExistsException("john@example.com"));

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers
                                    .containsString("john@example.com")));
        }

        @Test
        @DisplayName("Should return 400 when firstName is missing")
        void register_ShouldReturn400_WhenFirstNameMissing() throws Exception {
            validRegisterRequest.setFirstName(null);

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.firstName").exists());

            // Service should never be called if validation fails
            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("Should return 400 when email format is invalid")
        void register_ShouldReturn400_WhenEmailInvalid() throws Exception {
            validRegisterRequest.setEmail("not-an-email");

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("Should return 400 when password is too weak")
        void register_ShouldReturn400_WhenPasswordWeak() throws Exception {
            validRegisterRequest.setPassword("weak");

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("Should return 400 when request body is empty")
        void register_ShouldReturn400_WhenBodyIsEmpty() throws Exception {
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors").exists());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("Should return 400 when firstName is too short")
        void register_ShouldReturn400_WhenFirstNameTooShort() throws Exception {
            validRegisterRequest.setFirstName("J");  // min 2 chars

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.firstName").exists());
        }
    }

    // ─── Login Tests ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/login")
    class LoginTests {

        @Test
        @DisplayName("Should return 200 with token when login succeeds")
        void login_ShouldReturn200_WithAuthResponse() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(mockAuthResponse);

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validLoginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.email").value("john@example.com"))
                    .andExpect(jsonPath("$.user.password").doesNotExist());

            verify(authService).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("Should return 401 when credentials are invalid")
        void login_ShouldReturn401_WhenBadCredentials() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BadCredentialsException());

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validLoginRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message")
                            .value("Invalid email or password"));
        }

        @Test
        @DisplayName("Should return 400 when email is missing")
        void login_ShouldReturn400_WhenEmailMissing() throws Exception {
            validLoginRequest.setEmail(null);

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validLoginRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("Should return 400 when password is missing")
        void login_ShouldReturn400_WhenPasswordMissing() throws Exception {
            validLoginRequest.setPassword(null);

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validLoginRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("Should return 400 when email format is invalid")
        void login_ShouldReturn400_WhenEmailFormatInvalid() throws Exception {
            validLoginRequest.setEmail("not-valid");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validLoginRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verify(authService, never()).login(any());
        }
    }
}
