package com.company.usermanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RequestIdFilterTest {

    private RequestIdFilter requestIdFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        requestIdFilter = new RequestIdFilter();
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @Test
    void whenNoRequestIdHeader_shouldGenerateNewId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        requestIdFilter.doFilterInternal(request, response, filterChain);

        String requestId = response.getHeader("X-Request-ID");
        assertThat(requestId).isNotNull();
        assertThat(UUID.fromString(requestId)).isNotNull(); // Should be a valid UUID
        
        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get("requestId")).isNull(); // Cleared after request
    }

    @Test
    void whenValidRequestIdHeader_shouldUseIt() throws ServletException, IOException {
        String existingId = "client-req-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", existingId);
        request.setMethod("POST");
        request.setRequestURI("/api/resource");
        MockHttpServletResponse response = new MockHttpServletResponse();

        requestIdFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Request-ID")).isEqualTo(existingId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void whenInvalidRequestIdHeader_shouldGenerateNewId() throws ServletException, IOException {
        String invalidId = "bad-id-with-symbols-@#$%";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", invalidId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        requestIdFilter.doFilterInternal(request, response, filterChain);

        String requestId = response.getHeader("X-Request-ID");
        assertThat(requestId).isNotNull();
        assertThat(requestId).isNotEqualTo(invalidId);
    }

    @Test
    void shouldPopulateMDCDuringRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("PUT");
        request.setRequestURI("/api/data");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("requestId")).isNotNull();
            assertThat(MDC.get("method")).isEqualTo("PUT");
            assertThat(MDC.get("path")).isEqualTo("/api/data");
            return null;
        }).when(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        requestIdFilter.doFilterInternal(request, response, filterChain);
    }
}
