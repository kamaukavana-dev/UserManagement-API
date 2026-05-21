package com.company.usermanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SecurityHeadersConfigTest {

    private SecurityHeadersConfig securityHeadersConfig;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        securityHeadersConfig = new SecurityHeadersConfig();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldAddBasicSecurityHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityHeadersConfig.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("X-XSS-Protection")).isEqualTo("0");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAddCacheHeadersForAuthPaths() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityHeadersConfig.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeader("Expires")).isEqualTo("0");
    }

    @Test
    void shouldAddStrictCspForNormalPaths() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/data");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityHeadersConfig.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'");
    }

    @Test
    void shouldAddRelaxedCspForSwaggerPaths() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityHeadersConfig.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("Content-Security-Policy")).contains("script-src 'self' 'unsafe-inline'");
    }

    @Test
    void shouldAddHstsWhenRequestIsSecure() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setServletPath("/api/data");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityHeadersConfig.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("Strict-Transport-Security")).contains("max-age=31536000");
    }

    @Test
    void shouldNotAddHstsWhenRequestIsNotSecure() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(false);
        request.setServletPath("/api/data");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityHeadersConfig.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }
}
