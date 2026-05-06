package com.company.usermanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns a unique request ID to every incoming HTTP request.
 *
 * MDC (Mapped Diagnostic Context) is a thread-local map that SLF4J
 * attaches to every log line on that thread.
 *
 * Result: every log line for a request includes:
 *   [requestId=abc-123] JWT authenticated — user: john@example.com
 *   [requestId=abc-123] Fetching user by id: 5
 *   [requestId=abc-123] GET /users/5 completed in 45ms
 *
 * This makes it trivial to grep all logs for a single request
 * in Datadog, Grafana, CloudWatch, or any log aggregator.
 *
 * @Order(0) — runs before rate limiting and JWT filters
 */
@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final String MDC_METHOD_KEY = "method";
    private static final String MDC_PATH_KEY = "path";
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = sanitizeRequestId(request.getHeader(REQUEST_ID_HEADER));

        // Populate MDC — these appear in every log line for this request
        MDC.put(MDC_REQUEST_ID_KEY, requestId);
        MDC.put(MDC_METHOD_KEY, request.getMethod());
        MDC.put(MDC_PATH_KEY, request.getRequestURI());

        // Echo the request ID back in the response header
        // Frontend/mobile apps can use this to correlate client logs with server logs
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
            MDC.remove(MDC_METHOD_KEY);
            MDC.remove(MDC_PATH_KEY);
        }
    }

    private String sanitizeRequestId(String requestId) {
        if (requestId != null) {
            String trimmed = requestId.trim();
            if (SAFE_REQUEST_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }
}
