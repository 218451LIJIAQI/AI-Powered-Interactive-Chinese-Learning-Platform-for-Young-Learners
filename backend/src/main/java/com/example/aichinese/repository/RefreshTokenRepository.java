package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Resolve ownership without taking a token lock ahead of the account lock. */
    @Query("select t.user.id from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Unlocked lookup by token hash; callers that mutate the token acquire the write lock via
     * {@code entityManager.refresh(token, LockModeType.PESSIMISTIC_WRITE)} after locking the owning account so only
     * one {@code SELECT ... FOR UPDATE} runs and any earlier L1 snapshot is replaced.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Omits {@code clearAutomatically} because callers keep mutating the managed {@code User} afterward. */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now
              , t.updatedAt = :now
            WHERE t.user.id = :userId
              AND t.revokedAt IS NULL
            """)
    int revokeActiveTokensForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * The user's tokens of one sign-in's rotation chain: that browser signs out, or the whole sign-in is revoked.
     * Omits {@code clearAutomatically} so the caller's managed {@code User} stays attached.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now
              , t.updatedAt = :now
            WHERE t.user.id = :userId
              AND t.familyId = :familyId
              AND t.revokedAt IS NULL
            """)
    int revokeActiveTokenFamilyOfUser(
            @Param("userId") Long userId,
            @Param("familyId") String familyId,
            @Param("now") LocalDateTime now);

    boolean existsByUserIdAndFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(
            Long userId, String familyId, LocalDateTime now);

    /**
     * Current-read check for an active browser session after the caller already holds the user's write lock
     * (matching the {@code users -> refresh_tokens} lock order of logout and rotation). {@code PESSIMISTIC_READ}
     * bypasses a REPEATABLE READ snapshot so a single-browser sign-out committed concurrently is always seen.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select t.id from RefreshToken t
            where t.user.id = :userId
              and t.familyId = :familyId
              and t.revokedAt is null
              and t.expiresAt > :now
            """)
    List<Long> findActiveSessionTokenIdsLocked(
            @Param("userId") Long userId,
            @Param("familyId") String familyId,
            @Param("now") LocalDateTime now);

    /** When a sign-in happened: the first token of its rotation chain. */
    @Query("select min(t.createdAt) from RefreshToken t where t.user.id = :userId and t.familyId = :familyId")
    LocalDateTime findSessionStart(@Param("userId") Long userId, @Param("familyId") String familyId);

    /** Omits {@code clearAutomatically} because account deletion continues mutating the managed {@code User}. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
