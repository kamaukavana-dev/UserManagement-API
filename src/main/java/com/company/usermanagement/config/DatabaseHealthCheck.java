package com.company.usermanagement.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Validates database connectivity during startup.
 *
 * If the DB is unreachable, fail fast instead of starting a partially
 * functional application.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseHealthCheck implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (result == null || result != 1) {
            throw new IllegalStateException("Database startup validation failed");
        }

        log.info("Database startup validation succeeded.");
    }
}
