package com.company.usermanagement.integration;

import com.company.usermanagement.testsupport.TestJwtSecrets;
import org.junit.jupiter.api.BeforeEach;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers + Flyway bootstrap for Postgres-backed integration tests.
 *
 * The container is started once per JVM, then the exact production migration set
 * is applied before the Spring Boot test context starts. The container lifecycle
 * is managed manually so the database state survives across multiple integration
 * test classes in the same Maven/Failsafe JVM.
 */
abstract class AbstractPostgresIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load()
                .migrate();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.multiTenancy", () -> "NONE");
        registry.add("app.jwt.secret", TestJwtSecrets::base64Secret);
        registry.add("app.jwt.key-id", () -> "test-current");
        registry.add("app.jwt.previous-secret", () -> "");
        registry.add("app.jwt.previous-key-id", () -> "test-previous");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE public.audit_log,
                               public.refresh_tokens,
                               public.users
                RESTART IDENTITY CASCADE
                """);
    }
}
