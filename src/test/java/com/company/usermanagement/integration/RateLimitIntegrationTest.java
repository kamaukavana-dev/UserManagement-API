package com.company.usermanagement.integration;

import com.company.usermanagement.config.TestConfig;
import com.company.usermanagement.UsermanagementApplication;
import com.company.usermanagement.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {UsermanagementApplication.class, TestConfig.class}
)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = {
        "app.rate-limit.backend=local",
        "app.rate-limit.auth-capacity=3",
        "app.rate-limit.auth-refill-tokens=3",
        "app.rate-limit.auth-refill-seconds=60"
})
@DisplayName("Rate Limit Integration Test")
class RateLimitIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /auth/register — should return 429 when auth limiter is exceeded")
    void register_ShouldReturn429_WhenLimiterExceeded() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                    .firstName("Rate")
                                    .lastName("Limit")
                                    .email("user" + i + "@example.com")
                                    .password("SecurePassword123!")
                                    .build())))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .firstName("Rate")
                                .lastName("Limit")
                                .email("user4@example.com")
                                .password("SecurePassword123!")
                                .build())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message")
                        .value(containsString("Rate limit exceeded")));
    }
}
