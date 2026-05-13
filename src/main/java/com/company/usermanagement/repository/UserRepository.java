package com.company.usermanagement.repository;


import com.company.usermanagement.entity.User;
import com.company.usermanagement.entity.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Repository for User entity.
 *
 * Extends JpaRepository<User, Long> which gives us out of the box:
 *   save(), findById(), findAll(), deleteById(), count(), existsById()
 *   + pagination and sorting support
 *
 * We only add methods that JpaRepository doesn't already provide.
 *
 * Naming Convention for derived queries:
 *   findBy{Field}         → SELECT WHERE field = ?
 *   existsBy{Field}       → SELECT COUNT WHERE field = ? > 0
 *   countBy{Field}        → SELECT COUNT WHERE field = ?
 *   deleteBy{Field}       → DELETE WHERE field = ?
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ─── Lookup Methods ───────────────────────────────────────────────────────

    /**
     * Used by Spring Security's UserDetailsService during authentication.
     * Returns Optional — forces the caller to handle the "not found" case
     * explicitly. Never return null from a repository method.
     *
     * Generated SQL:
     *   SELECT * FROM users WHERE email = ? LIMIT 1
     */
    Optional<User> findByEmail(String email);

    /**
     * Used during registration to prevent duplicate accounts.
     * More efficient than findByEmail() + isPresent() —
     * generates SELECT COUNT instead of fetching the full row.
     *
     * Generated SQL:
     *   SELECT COUNT(*) > 0 FROM users WHERE email = ?
     */
    boolean existsByEmail(String email);

    // ─── Pagination & Filtering ───────────────────────────────────────────────

    /**
     * Fetch all users with a specific role — paginated.
     * Used by ADMIN endpoints to list users by role.
     *
     * Pageable carries: page number, page size, and sort direction.
     * Spring Data handles LIMIT/OFFSET automatically.
     */
    Page<User> findAllByRole(Role role, Pageable pageable);

    /**
     * Fetch all enabled OR disabled users — paginated.
     * Used by ADMIN to manage active/inactive accounts.
     */
    Page<User> findAllByEnabled(boolean enabled, Pageable pageable);

    /**
     * Search users by first name OR last name OR email — paginated.
     *
     * Why @Query instead of a derived name?
     * The derived method name would be:
     *   findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase
     * That's unreadable. @Query with JPQL is cleaner here.
     *
     * JPQL uses entity field names (firstName, lastName) not column names (first_name).
     * LOWER() makes the search case-insensitive without a DB index on lower(email).
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Fetch users created between two timestamps — paginated.
     * Useful for admin reporting and audit dashboards.
     */
    Page<User> findAllByCreatedAtBetween(
            ZonedDateTime from,
            ZonedDateTime to,
            Pageable pageable
    );

    // ─── Statistics Queries ───────────────────────────────────────────────────

    /**
     * Count users by role — used for admin dashboard stats.
     * More efficient than findAllByRole().getTotalElements()
     * because it doesn't need pagination overhead.
     */
    long countByRole(Role role);

    /**
     * Count active users — used for health/monitoring endpoints.
     */
    long countByEnabled(boolean enabled);
}
