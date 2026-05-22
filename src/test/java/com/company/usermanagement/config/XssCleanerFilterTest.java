package com.company.usermanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class XssCleanerFilterTest {

    private XssCleanerFilter xssCleanerFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        xssCleanerFilter = new XssCleanerFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldSanitizeHtmlTags() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("name", "<script>alert('xss')</script>John");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletRequest wrappedRequest = invocation.getArgument(0);
            assertThat(wrappedRequest.getParameter("name")).isEqualTo("alert('xss')John");
            return null;
        }).when(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        xssCleanerFilter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldSanitizeJavascriptProtocol() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("url", "javascript:alert('xss')");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletRequest wrappedRequest = invocation.getArgument(0);
            assertThat(wrappedRequest.getParameter("url")).isEqualTo("alert('xss')");
            return null;
        }).when(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        xssCleanerFilter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldSanitizeEventHandlers() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("bio", "Hello <img src=x onerror=alert(1)>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletRequest wrappedRequest = invocation.getArgument(0);
            // After removing <img src=x onerror=alert(1)>, it's "Hello "
            // Note: The regex for HTML tags removes everything between < and >
            assertThat(wrappedRequest.getParameter("bio")).isEqualTo("Hello");
            return null;
        }).when(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        xssCleanerFilter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldSanitizeParameterValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("tags", new String[]{"<script>", "<b>bold</b>", "clean"});
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletRequest wrappedRequest = invocation.getArgument(0);
            String[] values = wrappedRequest.getParameterValues("tags");
            assertThat(values).containsExactly("", "bold", "clean");
            return null;
        }).when(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        xssCleanerFilter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldHandleNullValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletRequest wrappedRequest = invocation.getArgument(0);
            assertThat(wrappedRequest.getParameter("nonexistent")).isNull();
            return null;
        }).when(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        xssCleanerFilter.doFilterInternal(request, response, filterChain);
    }
}
