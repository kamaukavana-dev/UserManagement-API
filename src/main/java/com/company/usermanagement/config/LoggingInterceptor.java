package com.company.usermanagement.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Logs every HTTP request with method, path, status, and duration.
 *
 * Output example:
 *   POST /auth/login → 200 OK [145ms]
 *   GET  /users/5   → 404 Not Found [12ms]
 *
 * Uses HandlerInterceptor (not a Filter) because we want access
 * to the resolved handler name and Spring MVC context.
 *
 * preHandle  — runs before the controller method
 * afterCompletion — runs after response is committed
 */
@Component
@Slf4j
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;    // true = continue processing the request
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime != null
                ? System.currentTimeMillis() - startTime
                : -1;

        int status = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Log level based on status code:
        // 2xx/3xx = INFO, 4xx = WARN, 5xx = ERROR
        if (status >= 500) {
            log.error("{} {} → {} [{}ms]", method, uri, status, duration);
        } else if (status >= 400) {
            log.warn("{} {} → {} [{}ms]", method, uri, status, duration);
        } else {
            log.info("{} {} → {} [{}ms]", method, uri, status, duration);
        }
    }
}