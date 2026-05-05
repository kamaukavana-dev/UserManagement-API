package com.company.usermanagement.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

public final class ApiPaths {

    public static final String[] PUBLIC_ENDPOINTS = {
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**",
            "/error",
            "/actuator/health",
            "/actuator/info"
    };

    public static final String[] SECURED_ACTUATOR_ENDPOINTS = {
            "/actuator/metrics/**",
            "/actuator/prometheus"
    };

    public static final String[] AUTH_ENDPOINTS = {
            "/auth/login",
            "/auth/register",
            "/auth/refresh"
    };

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private ApiPaths() {
    }

    public static boolean isPublicPath(String path) {
        return matchesAny(path, PUBLIC_ENDPOINTS);
    }

    public static boolean isAuthEndpoint(String path) {
        return matchesAny(path, AUTH_ENDPOINTS);
    }

    public static boolean isSecuredActuatorPath(String path) {
        return matchesAny(path, SECURED_ACTUATOR_ENDPOINTS);
    }

    public static String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath == null || contextPath.isBlank()) {
            return requestUri;
        }

        if (requestUri != null && requestUri.startsWith(contextPath)) {
            String normalized = requestUri.substring(contextPath.length());
            return normalized.isEmpty() ? "/" : normalized;
        }

        return requestUri;
    }

    private static boolean matchesAny(String path, String[] patterns) {
        if (path == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
