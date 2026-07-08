package com.company.usermanagement;

import com.company.usermanagement.config.TestcontainersConfig;
import com.company.usermanagement.repository.AuditLogRepository;
import com.company.usermanagement.repository.RefreshTokenRepository;
import com.company.usermanagement.repository.UserRepository;
import com.company.usermanagement.testsupport.TestJwtSecrets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class UsermanagementApplicationTests {

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @DynamicPropertySource
    static void registerSecrets(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecrets::base64Secret);
    }

    @Test
    void contextLoads() {
    }
}
