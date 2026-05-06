package com.company.usermanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Sanitizes all incoming string parameters to prevent XSS attacks.
 *
 * Uses HttpServletRequestWrapper to intercept and clean all
 * getParameter() calls transparently — no changes needed in controllers.
 *
 * Why strip HTML here and not in the service?
 * Defense in depth. The service layer should never receive raw HTML.
 * Sanitizing at the filter level means ALL paths are covered —
 * even future controllers you forget to sanitize manually.
 *
 * NOTE: This filter sanitizes query parameters and form fields only.
 * JSON request bodies are not sanitized here. XSS prevention for JSON
 * relies on @Valid annotations, output encoding, and Content-Security-Policy headers.
 * This is intentional; sanitizing arbitrary JSON would break binary/structured data.
 * We intentionally do not mutate request headers because that can
 * corrupt Authorization, tracing, and proxy headers.
 */
@Component
@Order(2)
public class XssCleanerFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        filterChain.doFilter(new XssRequestWrapper(request), response);
    }

    /**
     * Wraps the request and overrides getParameter methods
     * to return sanitized values.
     */
    static class XssRequestWrapper extends HttpServletRequestWrapper {

        // Matches HTML tags: <script>, <img onerror=...>, etc.
        private static final Pattern HTML_TAG_PATTERN =
                Pattern.compile("<[^>]+>", Pattern.CASE_INSENSITIVE);

        // Matches javascript: protocol in attributes
        private static final Pattern JS_PATTERN =
                Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE);

        // Matches event handlers: onclick=, onerror=, etc.
        private static final Pattern EVENT_HANDLER_PATTERN =
                Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE);

        public XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return sanitize(value);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;

            String[] sanitized = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitized[i] = sanitize(values[i]);
            }
            return sanitized;
        }

        private String sanitize(String value) {
            if (value == null) return null;

            String cleaned = HTML_TAG_PATTERN.matcher(value).replaceAll("");
            cleaned = JS_PATTERN.matcher(cleaned).replaceAll("");
            cleaned = EVENT_HANDLER_PATTERN.matcher(cleaned).replaceAll("");

            return cleaned.trim();
        }
    }
}
