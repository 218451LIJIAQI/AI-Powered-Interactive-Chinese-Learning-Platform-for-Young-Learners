package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @EntityGraph(attributePaths = { "user", "user.roles" })
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now
            WHERE t.user.id = :userId
              AND t.revokedAt IS NULL
            """)
    int revokeActiveTokensForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now
            WHERE t.familyId = :familyId
              AND t.revokedAt IS NULL
            """)
    int revokeActiveTokenFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);
}
