package com.company.usermanagement.repository;

import com.company.usermanagement.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revokedAt = :revokedAt
             WHERE rt.user.id = :userId
               AND rt.revokedAt IS NULL
            """)
    int revokeAllActiveByUserId(
            @Param("userId") Long userId,
            @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM RefreshToken rt
             WHERE rt.user.id = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);
}
