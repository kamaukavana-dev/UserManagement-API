package com.company.usermanagement.controller;

import com.company.usermanagement.dto.request.AdminCreateUserRequest;
import com.company.usermanagement.dto.request.ChangePasswordRequest;
import com.company.usermanagement.dto.request.UpdateUserRequest;
import com.company.usermanagement.dto.response.PagedResponse;
import com.company.usermanagement.dto.response.UserResponse;
import com.company.usermanagement.entity.enums.Role;
import com.company.usermanagement.exception.GlobalExceptionHandler;
import com.company.usermanagement.service.UserService;
import com.company.usermanagement.testsupport.TestJwtSecrets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.company.usermanagement.security.JwtAuthenticationFilter;
import com.company.usermanagement.security.RateLimitFilter;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitFilter.class}
        )
)
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, com.company.usermanagement.config.TestConfig.class})
@DisplayName("UserController Unit Tests")
class UserControllerTest {

    @DynamicPropertySource
    static void registerSecrets(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecrets::base64Secret);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private UserResponse mockUserResponse;

    @BeforeEach
    void setUp() {
        mockUserResponse = UserResponse.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .role(Role.ROLE_USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("GET /users/me")
    class GetMeTests {
        @Test
        @WithMockUser(username = "john@example.com")
        @DisplayName("Should return 200 with current user profile")
        void getCurrentUser_ShouldReturn200() throws Exception {
            when(userService.getCurrentUser("john@example.com")).thenReturn(mockUserResponse);

            mockMvc.perform(get("/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("john@example.com"))
                    .andExpect(jsonPath("$.firstName").value("John"));

            verify(userService).getCurrentUser("john@example.com");
        }
    }

    @Nested
    @DisplayName("PUT /users/me")
    class UpdateMeTests {
        @Test
        @WithMockUser(username = "john@example.com")
        @DisplayName("Should return 200 after updating profile")
        void updateCurrentUser_ShouldReturn200() throws Exception {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setFirstName("Johnny");
            request.setLastName("Doe");

            when(userService.getCurrentUser("john@example.com")).thenReturn(mockUserResponse);
            when(userService.updateUser(eq(1L), any(UpdateUserRequest.class))).thenReturn(mockUserResponse);

            mockMvc.perform(put("/users/me")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(userService).updateUser(eq(1L), any(UpdateUserRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /users/me/password")
    class ChangePasswordTests {
        @Test
        @WithMockUser(username = "john@example.com")
        @DisplayName("Should return 200 after changing password")
        void changeCurrentPassword_ShouldReturn200() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("OldPassword123!");
            request.setNewPassword("NewPassword123!");

            when(userService.changePassword(eq("john@example.com"), any(ChangePasswordRequest.class)))
                    .thenReturn(mockUserResponse);

            mockMvc.perform(put("/users/me/password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(userService).changePassword(eq("john@example.com"), any(ChangePasswordRequest.class));
        }
    }

    @Nested
    @DisplayName("Admin Operations")
    class AdminTests {
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET /users should return paged users")
        void getAllUsers_ShouldReturnPagedResponse() throws Exception {
            PagedResponse<UserResponse> pagedResponse = new PagedResponse<>(
                    List.of(mockUserResponse), 0, 1, 1, 1, true, false
            );

            when(userService.getAllUsers(anyInt(), anyInt(), anyString(), anyString()))
                    .thenReturn(pagedResponse);

            mockMvc.perform(get("/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].email").value("john@example.com"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET /users/search should return paged users")
        void searchUsers_ShouldReturnPagedResponse() throws Exception {
            PagedResponse<UserResponse> pagedResponse = new PagedResponse<>(
                    List.of(mockUserResponse), 0, 1, 1, 1, true, false
            );

            when(userService.searchUsers(anyString(), anyInt(), anyInt()))
                    .thenReturn(pagedResponse);

            mockMvc.perform(get("/users/search")
                            .param("keyword", "john"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].email").value("john@example.com"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET /users/role/{role} should return paged users")
        void getUsersByRole_ShouldReturnPagedResponse() throws Exception {
            PagedResponse<UserResponse> pagedResponse = new PagedResponse<>(
                    List.of(mockUserResponse), 0, 1, 1, 1, true, false
            );

            when(userService.getUsersByRole(any(Role.class), anyInt(), anyInt()))
                    .thenReturn(pagedResponse);

            mockMvc.perform(get("/users/role/ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].email").value("john@example.com"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("POST /users should return 201")
        void createUser_ShouldReturn201() throws Exception {
            AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("new@example.com")
                    .password("Password123!")
                    .role(Role.ROLE_ADMIN)
                    .build();

            when(userService.createUserByAdmin(any(AdminCreateUserRequest.class)))
                    .thenReturn(mockUserResponse);

            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("PUT /users/{id} should return 200")
        void updateUser_ShouldReturn200() throws Exception {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setFirstName("Johnny");

            when(userService.updateUser(eq(1L), any(UpdateUserRequest.class)))
                    .thenReturn(mockUserResponse);

            mockMvc.perform(put("/users/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("PATCH /users/{id}/role should return 200")
        void updateUserRole_ShouldReturn200() throws Exception {
            when(userService.updateUserRole(eq(1L), any(Role.class)))
                    .thenReturn(mockUserResponse);

            mockMvc.perform(patch("/users/1/role")
                            .with(csrf())
                            .param("role", "ROLE_ADMIN"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET /users/{id} should return user")
        void getUserById_ShouldReturnUser() throws Exception {
            when(userService.getUserById(1L)).thenReturn(mockUserResponse);

            mockMvc.perform(get("/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("DELETE /users/{id} should return 204")
        void deleteUser_ShouldReturn204() throws Exception {
            mockMvc.perform(delete("/users/1").with(csrf()))
                    .andExpect(status().isNoContent());

            verify(userService).deleteUser(1L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("PATCH /users/{id}/status should return 204")
        void setUserEnabled_ShouldReturn204() throws Exception {
            mockMvc.perform(patch("/users/1/status")
                            .with(csrf())
                            .param("enabled", "false"))
                    .andExpect(status().isNoContent());

            verify(userService).setUserEnabled(1L, false);
        }
    }
}
