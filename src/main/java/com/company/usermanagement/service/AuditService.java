package com.company.usermanagement.service;

import com.company.usermanagement.config.AppProperties;
import com.company.usermanagement.entity.AuditLog;
import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.AuditAction;
import com.company.usermanagement.repository.AuditLogRepository;
import com.company.usermanagement.security.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AppProperties appProperties;

    public void recordUserEvent(AuditAction action,
                                User subject,
                                Long actorUserId) {
        recordWithContext(action, "USER", subject == null ? null : subject.getId(), actorUserId);
    }

    public void recordUserEvent(AuditAction action,
                                Long subjectId,
                                Long actorUserId) {
        recordWithContext(action, "USER", subjectId, actorUserId);
    }

    public void recordTokenEvent(AuditAction action,
                                 Long subjectUserId,
                                 Long actorUserId) {
        recordWithContext(action, "REFRESH_TOKEN", subjectUserId, actorUserId);
    }

    /**
     * Captures request context (IP, UA) on the caller thread,
     * then persists asynchronously to avoid blocking the critical path.
     */
    private void recordWithContext(AuditAction action,
                                   String entityType,
                                   Long entityId,
                                   Long actorUserId) {
        String clientIp = resolveClientIp();
        String userAgent = resolveUserAgent();
        Long resolvedActorId = resolveActorUserId(actorUserId);

        // Handoff to background thread
        saveAsync(action, entityType, entityId, resolvedActorId, clientIp, userAgent);
    }

    @Async
    protected void saveAsync(AuditAction action,
                             String entityType,
                             Long entityId,
                             Long actorUserId,
                             String ipAddress,
                             String userAgent) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(actorUserId)
                    .action(action.name())
                    .entityType(entityType)
                    .entityId(entityId)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.error("Failed to persist audit log asynchronously: {}", ex.getMessage());
        }
    }

    private Long resolveActorUserId(Long actorUserId) {
        if (actorUserId != null) {
            return actorUserId;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    private String resolveClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            return IpAddressUtils.extractClientIp(
                    request,
                    appProperties.getRateLimit().getTrustedProxyCidrs());
        }
        return "system";
    }

    private String resolveUserAgent() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            return request.getHeader("User-Agent");
        }
        return "system";
    }
}
