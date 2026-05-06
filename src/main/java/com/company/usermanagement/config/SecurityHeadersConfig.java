package com.company.usermanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security headers to every HTTP response.
 *
 * These headers instruct browsers to enforce security policies
 * that protect users from common attacks.
 *
 * Most headers are also configured in SecurityConfig but this
 * filter adds additional ones not covered by Spring Security.
 */
@Component
@Order(1)
public class SecurityHeadersConfig extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // Prevent browsers from MIME-sniffing away from declared content type
        response.setHeader("X-Content-Type-Options", "nosniff");

        // API responses should never be cached by browsers or proxies.
        if (request.getServletPath().startsWith("/auth/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }

        // Swagger UI requires a slightly relaxed CSP in non-production.
        if (request.getServletPath().startsWith("/swagger-ui")
                || request.getServletPath().startsWith("/api-docs")
                || request.getServletPath().startsWith("/v3/api-docs")) {
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline'; " +
                            "style-src 'self' 'unsafe-inline'; " +
                            "img-src 'self' data:; " +
                            "frame-ancestors 'none'; " +
                            "form-action 'self'");
        } else {
            response.setHeader("Content-Security-Policy",
                    "default-src 'none'; " +
                            "frame-ancestors 'none'; " +
                            "base-uri 'none'; " +
                            "form-action 'none'");
        }

        // Tells browsers this site should only be accessed via HTTPS.
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains; preload");
        }

        // Prevents the page from being loaded in an iframe (clickjacking)
        response.setHeader("X-Frame-Options", "DENY");

        // Disables browser's built-in XSS filter (modern browsers use CSP instead)
        // Setting to 0 actually prevents some filter bypass attacks
        response.setHeader("X-XSS-Protection", "0");

        // Controls how much referrer info is sent with requests
        response.setHeader("Referrer-Policy",
                "strict-origin-when-cross-origin");

        // Restricts browser features (camera, microphone, etc.)
        response.setHeader("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), " +
                "payment=(), usb=(), magnetometer=()");

        filterChain.doFilter(request, response);
    }
}
