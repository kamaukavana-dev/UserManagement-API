package com.company.usermanagement.mapper;

import com.company.usermanagement.dto.response.UserResponse;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private final UserMapper userMapper = new UserMapper();

    @Test
    @DisplayName("Should map User entity to UserResponse DTO")
    void toResponse_ShouldMapCorrectly() {
        Instant now = Instant.now();
        User user = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .role(Role.ROLE_USER)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        UserResponse response = userMapper.toResponse(user);

        assertNotNull(response);
        assertEquals(user.getId(), response.getId());
        assertEquals(user.getFirstName(), response.getFirstName());
        assertEquals(user.getLastName(), response.getLastName());
        assertEquals(user.getEmail(), response.getEmail());
        assertEquals(user.getRole(), response.getRole());
        assertEquals(user.isEnabled(), response.isEnabled());
        assertEquals(user.getCreatedAt(), response.getCreatedAt());
        assertEquals(user.getUpdatedAt(), response.getUpdatedAt());
    }
}
