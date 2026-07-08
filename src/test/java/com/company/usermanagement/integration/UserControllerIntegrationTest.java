package com.company.usermanagement.integration;

import com.company.usermanagement.config.TestConfig;
import com.company.usermanagement.UsermanagementApplication;
import com.company.usermanagement.dto.request.AdminCreateUserRequest;
import com.company.usermanagement.dto.request.ChangePasswordRequest;
import com.company.usermanagement.dto.request.LoginRequest;
import com.company.usermanagement.dto.request.RegisterRequest;
import com.company.usermanagement.dto.request.UpdateUserRequest;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.Role;
import com.company.usermanagement.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {UsermanagementApplication.class, TestConfig.class}
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@DisplayName("User Controller Integration Tests")
class UserControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String userToken;
    private Long testUserId;

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.saveAndFlush(User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin.integration@example.com")
                .password(passwordEncoder.encode("AdminSecurePassword123!"))
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .accountNonLocked(true)
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        adminToken = loginAndGetToken(admin.getEmail(), "AdminSecurePassword123!");

        // Register and login a test USER
        registerUser("Test", "User", "testuser@example.com", "SecurePassword123!");
        userToken = loginAndGetToken("testuser@example.com", "SecurePassword123!");

        // Get the test user's ID
        MvcResult result = mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        testUserId = objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("GET /users — ADMIN should get paginated user list")
    void getAllUsers_ShouldReturn200_ForAdmin() throws Exception {
        mockMvc.perform(get("/users")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /users/{id} — USER should be forbidden from accessing another user")
    void getUserById_ShouldReturn403_ForUserRole() throws Exception {
        mockMvc.perform(get("/users/{id}", testUserId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("GET /users/{id} — ADMIN should get user by ID")
    void getUserById_ShouldReturn200_ForAdmin() throws Exception {
        mockMvc.perform(get("/users/{id}", testUserId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUserId))
                .andExpect(jsonPath("$.email").value("testuser@example.com"));
    }

    @Test
    @DisplayName("GET /users/search — ADMIN should search users by keyword")
    void searchUsers_ShouldReturn200_ForAdmin() throws Exception {
        mockMvc.perform(get("/users/search")
                .header("Authorization", "Bearer " + adminToken)
                .param("keyword", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email")
                        .value("testuser@example.com"));
    }

    @Test
    @DisplayName("PUT /users/{id} — ADMIN should update user")
    void updateUser_ShouldReturn200_ForAdmin() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("UpdatedFirst")
                .build();

        mockMvc.perform(put("/users/{id}", testUserId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("UpdatedFirst"));
    }

    @Test
    @DisplayName("PUT /users/me/password — authenticated user should change password")
    void changeCurrentPassword_ShouldReturn200_AndAllowLoginWithNewPassword() throws Exception {
        String token = userToken;
        String newPassword = "NewSecurePassword123!";

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("SecurePassword123!")
                .newPassword(newPassword)
                .build();

        mockMvc.perform(put("/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("testuser@example.com"));

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        String newToken = loginAndGetToken("testuser@example.com", newPassword);
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("testuser@example.com"));
    }

    @Test
    @DisplayName("POST /users — ADMIN should create user with explicit role")
    void createUser_ShouldReturn201_WithAdminRole() throws Exception {
        String email = "createdadmin@example.com";
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .firstName("Created")
                .lastName("Admin")
                .email(email)
                .password("CreatedAdminSecure123!")
                .role(Role.ROLE_ADMIN)
                .build();

        mockMvc.perform(post("/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));

        String createdAdminToken = loginAndGetToken(email, "CreatedAdminSecure123!");
        mockMvc.perform(get("/users")
                .header("Authorization", "Bearer " + createdAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /users/{id}/status — ADMIN should disable user")
    void setUserEnabled_ShouldReturn204_ForAdmin() throws Exception {
        mockMvc.perform(patch("/users/{id}/status", testUserId)
                .header("Authorization", "Bearer " + adminToken)
                .param("enabled", "false"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("DELETE /users/{id} — ADMIN should soft-delete user")
    void deleteUser_ShouldReturn204_ForAdmin() throws Exception {
        // Register a disposable user
        registerUser("Delete", "Me", "deleteme@example.com", "SecurePassword123!");
        String deleteToken = loginAndGetToken("deleteme@example.com", "SecurePassword123!");

        MvcResult result = mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + deleteToken))
                .andReturn();
        Long deleteUserId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/users/{id}", deleteUserId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + deleteToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /users/{id} — should return 404 for non-existent ID")
    void getUserById_ShouldReturn404_WhenNotFound() throws Exception {
        mockMvc.perform(get("/users/{id}", 999999L)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ─── Privilege-escalation negative paths (valid USER token, admin-only ops) ──

    @Test
    @DisplayName("PATCH /users/{id}/role — USER token cannot escalate a role (403, role unchanged)")
    void updateUserRole_ShouldReturn403_ForUserRole_AndNotChangeRole() throws Exception {
        mockMvc.perform(patch("/users/{id}/role", testUserId)
                        .header("Authorization", "Bearer " + userToken)
                        .param("role", "ROLE_ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // Server-side authorization must hold: the role in the DB is untouched.
        User after = userRepository.findById(testUserId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(after.getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @DisplayName("POST /users — USER token cannot create accounts (403, no user created)")
    void createUser_ShouldReturn403_ForUserRole() throws Exception {
        long before = userRepository.count();

        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .firstName("Mallory")
                .lastName("Escalator")
                .email("mallory.escalation@example.com")
                .password("AttackerSecurePassword123!")
                .role(Role.ROLE_ADMIN)
                .build();

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        org.assertj.core.api.Assertions.assertThat(userRepository.count()).isEqualTo(before);
        org.assertj.core.api.Assertions.assertThat(
                userRepository.findByEmail("mallory.escalation@example.com")).isEmpty();
    }

    @Test
    @DisplayName("DELETE /users/{id} — USER token cannot soft-delete (403, account still enabled)")
    void deleteUser_ShouldReturn403_ForUserRole_AndNotDisableAccount() throws Exception {
        mockMvc.perform(delete("/users/{id}", testUserId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        User after = userRepository.findById(testUserId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(after.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("PATCH /users/{id}/status — USER token cannot disable an account (403, still enabled)")
    void setUserEnabled_ShouldReturn403_ForUserRole() throws Exception {
        mockMvc.perform(patch("/users/{id}/status", testUserId)
                        .header("Authorization", "Bearer " + userToken)
                        .param("enabled", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        User after = userRepository.findById(testUserId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(after.isEnabled()).isTrue();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void registerUser(String firstName, String lastName,
            String email, String password) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .password(password)
                .build();

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
