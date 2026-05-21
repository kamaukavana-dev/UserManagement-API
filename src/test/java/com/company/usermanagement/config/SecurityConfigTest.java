package com.company.usermanagement.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import com.company.usermanagement.testsupport.TestJwtSecrets;
import com.company.usermanagement.security.JwtService;
import com.company.usermanagement.security.RestAccessDeniedHandler;
import com.company.usermanagement.security.RestAuthenticationEntryPoint;
import com.company.usermanagement.security.UserDetailsServiceImpl;
import com.company.usermanagement.service.RateLimitService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("SecurityConfig Integration Tests")
class SecurityConfigTest {

    @TestConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestBootstrap {}

    @SpringBootTest(classes = {SecurityConfig.class, TestBootstrap.class},
            properties = {
                    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
            })
    @ActiveProfiles("test")
    @DisplayName("CORS default configuration test")
    static class DefaultCorsTests {
        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
            registry.add("app.jwt.secret", TestJwtSecrets::base64Secret);
            // Ensure allowed-origins is EMPTY to trigger the default branch
            registry.add("app.cors.allowed-origins", () -> "");
        }

        @Autowired
        private CorsConfigurationSource corsConfigurationSource;

        @MockBean
        private JdbcTemplate jdbcTemplate;

        @MockBean
        private JwtService jwtService;

        @MockBean
        private UserDetailsServiceImpl userDetailsService;

        @MockBean
        private RateLimitService rateLimitService;

        @MockBean
        private RestAuthenticationEntryPoint authenticationEntryPoint;

        @MockBean
        private RestAccessDeniedHandler accessDeniedHandler;

        @BeforeEach
        void stubDatabaseHealthCheck() {
            when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        }

        @Test
        @DisplayName("Should use dev defaults when no origins configured")
        void corsConfigurationSource_ShouldReturnDevDefaults() {
            CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest());
            assertThat(config).isNotNull();
            assertThat(config.getAllowedOrigins()).contains("http://localhost:3000");
        }
    }

    @SpringBootTest(classes = {SecurityConfig.class, TestBootstrap.class},
            properties = {
                    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
            })
    @ActiveProfiles("test")
    @DisplayName("CORS explicit configuration test")
    static class ExplicitCorsTests {
        @DynamicPropertySource
        static void registerProperties(DynamicPropertyRegistry registry) {
            registry.add("app.jwt.secret", TestJwtSecrets::base64Secret);
            registry.add("app.cors.allowed-origins", () -> "https://myapp.com");
        }

        @Autowired
        private CorsConfigurationSource corsConfigurationSource;

        @MockBean
        private JdbcTemplate jdbcTemplate;

        @MockBean
        private JwtService jwtService;

        @MockBean
        private UserDetailsServiceImpl userDetailsService;

        @MockBean
        private RateLimitService rateLimitService;

        @MockBean
        private RestAuthenticationEntryPoint authenticationEntryPoint;

        @MockBean
        private RestAccessDeniedHandler accessDeniedHandler;

        @BeforeEach
        void stubDatabaseHealthCheck() {
            when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        }

        @Test
        @DisplayName("Should use configured origins when provided")
        void corsConfigurationSource_ShouldReturnConfiguredOrigins() {
            CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest());
            assertThat(config).isNotNull();
            assertThat(config.getAllowedOrigins()).containsExactly("https://myapp.com");
        }
    }
}
