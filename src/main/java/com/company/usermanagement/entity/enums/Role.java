package com.company.usermanagement.entity.enums;
/**
 * User roles for role-based access control (RBAC).
 *
 * Stored as VARCHAR in the database for portability across engines.
 *
 * Why prefix with ROLE_?
 * Spring Security's hasRole() method automatically prepends "ROLE_" when
 * checking authorities. By storing ROLE_USER / ROLE_ADMIN, we stay
 * compatible with both hasRole("USER") and hasAuthority("ROLE_USER").
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
