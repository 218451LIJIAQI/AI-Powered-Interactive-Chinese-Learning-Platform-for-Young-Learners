package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user", "user.roles" })
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findLockedByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Transactional
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now
              , t.updatedAt = :now
            WHERE t.user.id = :userId
              AND t.revokedAt IS NULL
            """)
    int revokeActiveTokensForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now
              , t.updatedAt = :now
            WHERE t.familyId = :familyId
              AND t.revokedAt IS NULL
            """)
    int revokeActiveTokenFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);
}
