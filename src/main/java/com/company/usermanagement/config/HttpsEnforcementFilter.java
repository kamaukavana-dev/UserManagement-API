package com.company.usermanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces HTTPS for all requests.
 *
 * Why this matters:
 * - Defense-in-depth: Protects against misconfigured reverse proxies
 * - Prevents accidental HTTP exposure in development/staging
 * - Ensures credentials and JWTs are never transmitted over HTTP
 *
 * Exception: Allows health checks and actuator endpoints over HTTP for monitoring
 * (these should be on internal network only in production)
 *
 * Disabled in 'test' and 'dev' profiles to allow unit/integration testing over HTTP
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.security.https-enforcement.enabled", havingValue = "true", matchIfMissing = false)
public class HttpsEnforcementFilter extends OncePerRequestFilter {

    private static final String[] ALLOWED_HTTP_PATHS = {"/actuator", "/health"};

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = request.getScheme();
        }

        String requestUri = request.getRequestURI();
        boolean isAllowedHttpPath = isPathAllowed(requestUri);

        // In production, enforce HTTPS
        if (!"https".equalsIgnoreCase(scheme) && !isAllowedHttpPath) {
            log.debug("Redirecting {} request to HTTPS: {}", scheme, requestUri);
            String httpsUrl = "https://" + request.getServerName() + request.getRequestURI();
            if (request.getQueryString() != null) {
                httpsUrl += "?" + request.getQueryString();
            }
            response.sendRedirect(httpsUrl);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPathAllowed(String path) {
        for (String allowedPath : ALLOWED_HTTP_PATHS) {
            if (path.startsWith(allowedPath)) {
                return true;
            }
        }
        return false;
    }
}

