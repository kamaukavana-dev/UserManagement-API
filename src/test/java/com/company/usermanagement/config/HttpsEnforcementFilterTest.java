package com.company.usermanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpsEnforcementFilterTest {

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private HttpsEnforcementFilter httpsEnforcementFilter;

    @Test
    void shouldRedirectToHttpsWhenSchemeIsHttp() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setRequestURI("/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        httpsEnforcementFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("https://localhost/api/v1/users");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldAllowRequestWhenSchemeIsHttps() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setRequestURI("/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        httpsEnforcementFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAllowRequestWhenPathIsWhitelisted() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setRequestURI("/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        httpsEnforcementFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRespectXForwardedProtoHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http"); // Internal scheme
        request.addHeader("X-Forwarded-Proto", "https"); // External scheme from proxy
        request.setRequestURI("/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        httpsEnforcementFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
