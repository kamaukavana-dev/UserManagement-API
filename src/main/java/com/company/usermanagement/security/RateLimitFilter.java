package com.company.usermanagement.security;

import com.company.usermanagement.config.ApiPaths;
import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.exception.ErrorResponse;
import com.company.usermanagement.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RateLimitService rateLimitService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = ApiPaths.requestPath(request);
        boolean authEndpoint = ApiPaths.isAuthEndpoint(requestPath);
        String clientIp = resolveClientIp(request);
        String rateLimitKey = resolveRateLimitKey(request, authEndpoint, clientIp);

        try {
            RateLimitService.RateLimitDecision decision =
                    rateLimitService.check(rateLimitKey, authEndpoint);

            response.addHeader("X-Rate-Limit-Remaining",
                    String.valueOf(decision.remainingTokens()));

            if (decision.allowed()) {
                filterChain.doFilter(request, response);
                return;
            }

            response.addHeader("X-Rate-Limit-Retry-After-Seconds",
                    String.valueOf(decision.retryAfterSeconds()));
            response.addHeader("Retry-After",
                    String.valueOf(decision.retryAfterSeconds()));

            log.warn("Rate limit exceeded for key={} path={}", rateLimitKey, requestPath);
            writeTooManyRequests(response, decision.retryAfterSeconds());
        } catch (Exception ex) {
            log.error("Rate limiter failed closed for path {}: {}", requestPath, ex.getMessage());
            long retryAfterSeconds = authEndpoint
                    ? appProperties.getRateLimit().getAuthRefillSeconds()
                    : appProperties.getRateLimit().getRefillSeconds();
            writeTooManyRequests(response, Math.max(1, retryAfterSeconds));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = ApiPaths.requestPath(request);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !ApiPaths.isAuthEndpoint(path) && ApiPaths.isPublicPath(path);
    }

    private String resolveRateLimitKey(
            HttpServletRequest request,
            boolean authEndpoint,
            String clientIp) {

        // NOTE: Rate limiting applies a single key per request (IP for auth endpoints,
        // user ID for authenticated endpoints). Layered IP+user limiting is not
        // currently implemented. See SEC-007 in audit report for full assessment.
        // To implement: apply two bucket checks in sequence; fail on either threshold.
        if (authEndpoint) {
            return clientIp;
        }

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                if (jwtService.isAccessTokenStructureValid(token)) {
                    String email = jwtService.extractEmail(token);
                    if (StringUtils.hasText(email)) {
                        return "user:" + email.toLowerCase();
                    }
                }
            } catch (Exception ignored) {
                // Fall back to client IP.
            }
        }

        return "ip:" + clientIp;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return IpAddressUtils.extractClientIp(
                request,
                appProperties.getRateLimit().getTrustedProxyCidrs());
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Rate limit exceeded. Try again later.")
                .timestamp(Instant.now())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
