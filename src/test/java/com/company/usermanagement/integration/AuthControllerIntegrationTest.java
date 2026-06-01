package com.company.usermanagement.integration;

import com.company.usermanagement.config.TestConfig;
import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.UsermanagementApplication;
import com.company.usermanagement.dto.request.LoginRequest;
import com.company.usermanagement.dto.request.RegisterRequest;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.repository.UserRepository;
import com.company.usermanagement.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for authentication endpoints.
 *
 * @SpringBootTest — loads the complete Spring application context.
 * @AutoConfigureMockMvc — injects MockMvc for HTTP request simulation.
 * @Testcontainers — manages Docker container lifecycle via annotations.
 *
 * @Container with static — one Postgres container shared across all
 * tests in this class. Starting a container per test would be too slow.
 *
 * @DynamicPropertySource — overrides Spring datasource properties at
 * runtime with Testcontainer's dynamically assigned host/port.
 * This is how Testcontainers integrates with Spring Boot test context.
 *
 * @TestMethodOrder(MethodOrderer.OrderAnnotation.class) — ensures
 * register runs before login (login needs a registered user).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {UsermanagementApplication.class, TestConfig.class}
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@DisplayName("Auth Controller Integration Tests")
class AuthControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppProperties appProperties;

    private static final String DEFAULT_EMAIL = "john.integration@example.com";
    private static final String DEFAULT_PASSWORD = "SecurePassword123!";

    private String registerAndGetToken(String email, String password) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email(email)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void registerOnly(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .firstName("John")
                                .lastName("Doe")
                                .email(email)
                                .password(password)
                                .build())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /auth/register — should register user and return 201 with JWT")
    void register_ShouldReturn201_WithJwtToken() throws Exception {
        String token = registerAndGetToken(DEFAULT_EMAIL, DEFAULT_PASSWORD);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("POST /auth/register — should return 409 for duplicate email")
    void register_ShouldReturn409_WhenEmailAlreadyExists() throws Exception {
        registerOnly(DEFAULT_EMAIL, DEFAULT_PASSWORD);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                        .firstName("John")
                        .lastName("Doe")
                        .email(DEFAULT_EMAIL)
                        .password(DEFAULT_PASSWORD)
                        .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value(containsString(DEFAULT_EMAIL)));
    }

    @Test
    @DisplayName("POST /auth/register — should treat email as case-insensitive")
    void register_ShouldReturn409_WhenEmailDiffersOnlyByCase() throws Exception {
        registerOnly(DEFAULT_EMAIL, DEFAULT_PASSWORD);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                        .firstName("John")
                        .lastName("Doe")
                        .email("JOHN.INTEGRATION@EXAMPLE.COM")
                        .password(DEFAULT_PASSWORD)
                        .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString(DEFAULT_EMAIL)));
    }

    @Test
    @DisplayName("POST /auth/register — should return 400 for invalid input")
    void register_ShouldReturn400_ForInvalidInput() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("J")               // too short
                .email("not-an-email")         // invalid format
                .password("weak")              // doesn't meet requirements
                .build();

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists());
    }

    @Test
    @DisplayName("POST /auth/login — should return 200 with JWT for valid credentials")
    void login_ShouldReturn200_WithValidCredentials() throws Exception {
        registerOnly(DEFAULT_EMAIL, DEFAULT_PASSWORD);
        LoginRequest request = LoginRequest.builder()
                .email(DEFAULT_EMAIL)
                .password(DEFAULT_PASSWORD)
                .build();

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email")
                        .value(DEFAULT_EMAIL));
    }

    @Test
    @DisplayName("POST /auth/login — should return 401 for wrong password")
    void login_ShouldReturn401_ForWrongPassword() throws Exception {
        registerOnly(DEFAULT_EMAIL, DEFAULT_PASSWORD);
        LoginRequest request = LoginRequest.builder()
                .email(DEFAULT_EMAIL)
                .password("WrongPassword@123")
                .build();

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /auth/login — should return 401 for unknown email")
    void login_ShouldReturn401_ForUnknownEmail() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("nobody@example.com")
                .password("SecurePassword123")
                .build();

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));
    }

    @Test
    @DisplayName("GET /users/me — should return 401 without token")
    void getMe_ShouldReturn401_WithoutToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /users/me — should return 200 with valid token")
    void getMe_ShouldReturn200_WithValidToken() throws Exception {
        String token = registerAndGetToken(DEFAULT_EMAIL, DEFAULT_PASSWORD);

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email")
                        .value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/me — should reject expired JWTs")
    void getMe_ShouldReturn401_ForExpiredToken() throws Exception {
        registerOnly(DEFAULT_EMAIL, DEFAULT_PASSWORD);

        User user = userRepository.findByEmail(DEFAULT_EMAIL).orElseThrow();
        JwtService shortLivedJwtService = shortLivedJwtService();
        String expiredToken = shortLivedJwtService.generateToken(user);

        Thread.sleep(10L);

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /users/me — should reject tampered JWTs")
    void getMe_ShouldReturn401_ForTamperedToken() throws Exception {
        String token = registerAndGetToken(DEFAULT_EMAIL, DEFAULT_PASSWORD);
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /users/me — should reject revoked JWT after account disable")
    void getMe_ShouldReturn401_WhenAccountIsDisabled() throws Exception {
        String token = registerAndGetToken(DEFAULT_EMAIL, DEFAULT_PASSWORD);

        User user = userRepository.findByEmail(DEFAULT_EMAIL).orElseThrow();
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /users — should return 403 for USER role hitting ADMIN endpoint")
    void getAllUsers_ShouldReturn403_ForUserRole() throws Exception {
        String token = registerAndGetToken(DEFAULT_EMAIL, DEFAULT_PASSWORD);

        mockMvc.perform(get("/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private JwtService shortLivedJwtService() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret(appProperties.getJwt().getSecret());
        properties.getJwt().setKeyId(appProperties.getJwt().getKeyId());
        properties.getJwt().setPreviousSecret(appProperties.getJwt().getPreviousSecret());
        properties.getJwt().setPreviousKeyId(appProperties.getJwt().getPreviousKeyId());
        properties.getJwt().setExpirationMs(1L);
        properties.getJwt().setRefreshExpirationMs(appProperties.getJwt().getRefreshExpirationMs());

        JwtService jwtService = new JwtService(properties, objectMapper);
        try {
            Method init = JwtService.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(jwtService);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to initialize test JWT service", ex);
        }
        return jwtService;
    }
}
