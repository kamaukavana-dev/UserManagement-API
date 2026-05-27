package com.company.usermanagement.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogTest {

    @Test
    @DisplayName("Should update createdAt on create")
    void onCreate_ShouldSetTimestamp() {
        AuditLog log = new AuditLog();
        log.onCreate();
        assertNotNull(log.getCreatedAt());
    }
}
