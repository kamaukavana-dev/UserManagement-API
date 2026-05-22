package com.company.usermanagement.config;

import com.company.usermanagement.tenancy.TenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver() {
            @Override
            public String resolveCurrentTenantIdentifier() {
                return "default";
            }
        };
    }

    @Bean
    public HibernatePropertiesCustomizer testHibernatePropertiesCustomizer(TenantIdentifierResolver resolver) {
        return properties -> {
            // Ensure Hibernate uses a deterministic tenant id during test bootstrap.
            properties.put("hibernate.tenant_identifier_resolver", resolver);
            properties.put("hibernate.multi_tenant_identifier_resolver", resolver);
        };
    }
}

