package com.company.usermanagement.service;

import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.entity.AuditLog;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.AuditAction;
import com.company.usermanagement.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        AppProperties.RateLimit rateLimit = mock(AppProperties.RateLimit.class);
        lenient().when(appProperties.getRateLimit()).thenReturn(rateLimit);
        lenient().when(rateLimit.getTrustedProxyCidrs()).thenReturn(
                List.of("127.0.0.1/32", "10.0.0.0/8"));
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordUserEvent_WithExplicitActor_ShouldSaveAuditLog() {
        User subject = User.builder().id(2L).build();
        Long actorId = 1L;
        
        setupRequestMocks("127.0.0.1", "Mozilla/5.0");

        auditService.recordUserEvent(AuditAction.USER_LOGGED_IN, subject, actorId);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(actorId);
        assertThat(saved.getAction()).isEqualTo(AuditAction.USER_LOGGED_IN.name());
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getEntityId()).isEqualTo(2L);
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void recordUserEvent_WithResolvedActor_ShouldSaveAuditLog() {
        User actor = User.builder().id(1L).build();
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(actor);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        setupRequestMocks("192.168.1.1", "curl/7.64.1");

        auditService.recordUserEvent(AuditAction.USER_REGISTERED, 2L, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getAction()).isEqualTo(AuditAction.USER_REGISTERED.name());
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void recordTokenEvent_WithForwardedIp_ShouldUseCorrectIp() {
        setupRequestMocks("10.0.0.1", "Postman", "203.0.113.195, 70.41.3.18");

        auditService.recordTokenEvent(AuditAction.USER_REFRESHED_TOKEN, 1L, 1L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        
        AuditLog saved = captor.getValue();
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.195");
        assertThat(saved.getEntityType()).isEqualTo("REFRESH_TOKEN");
    }

    private void setupRequestMocks(String remoteAddr, String userAgent) {
        setupRequestMocks(remoteAddr, userAgent, null);
    }

    private void setupRequestMocks(String remoteAddr, String userAgent, String xForwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getRemoteAddr()).thenReturn(remoteAddr);
        lenient().when(request.getHeader("User-Agent")).thenReturn(userAgent);
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }
}
