package com.company.usermanagement.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    private static final String DEFAULT_TENANT = "default";
    // STUB: Always resolves to 'default' tenant.
    // Multi-tenancy is not active. See ARCH-001 in audit report.
    // Do not use in production multi-tenant scenarios without implementing
    // proper tenant context extraction from request headers or JWT claims.

    @Override
    public String resolveCurrentTenantIdentifier() {
        // For now, we'll use a default tenant.
        // In a real application, this would come from the security context.
        return DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
