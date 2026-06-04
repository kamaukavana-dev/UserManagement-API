package com.company.usermanagement.security;

import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private JwtService jwtService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AppProperties appProperties;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        AppProperties.RateLimit rateLimitProps = mock(AppProperties.RateLimit.class);
        lenient().when(appProperties.getRateLimit()).thenReturn(rateLimitProps);
        lenient().when(rateLimitProps.getTrustedProxyCidrs()).thenReturn(Collections.singletonList("127.0.0.1/32"));
    }

    @Test
    void shouldAllowRequestWhenWithinLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/users");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitService.RateLimitDecision decision = new RateLimitService.RateLimitDecision(true, 10, 0);
        when(rateLimitService.check(anyString(), anyBoolean())).thenReturn(decision);

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("10");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldBlockRequestWhenLimitExceeded() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/users");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitService.RateLimitDecision decision = new RateLimitService.RateLimitDecision(false, 0, 60);
        when(rateLimitService.check(anyString(), anyBoolean())).thenReturn(decision);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-Rate-Limit-Retry-After-Seconds")).isEqualTo("60");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldResolveClientIpFromXForwardedFor() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "192.168.1.1, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitService.RateLimitDecision decision = new RateLimitService.RateLimitDecision(true, 10, 0);
        when(rateLimitService.check(eq("ip:192.168.1.1"), anyBoolean())).thenReturn(decision);

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(rateLimitService).check(eq("ip:192.168.1.1"), anyBoolean());
    }

    @Test
    void shouldNotUseXForwardedForWhenProxyNotTrusted() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/users");
        request.setRemoteAddr("203.0.113.1"); // Untrusted IP
        request.addHeader("X-Forwarded-For", "192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitService.RateLimitDecision decision = new RateLimitService.RateLimitDecision(true, 10, 0);
        when(rateLimitService.check(eq("ip:203.0.113.1"), anyBoolean())).thenReturn(decision);

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(rateLimitService).check(eq("ip:203.0.113.1"), anyBoolean());
    }

    @Test
    void shouldHandleRateLimitServiceExceptionGracefully() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/users");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.check(anyString(), anyBoolean())).thenThrow(new RuntimeException("Redis error"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldNotFilterOptionsRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("OPTIONS");
        request.setServletPath("/api/v1/users");
        
        boolean shouldNotFilter = rateLimitFilter.shouldNotFilter(request);
        
        assertThat(shouldNotFilter).isTrue();
    }
}
