package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates every still-active reset token for the given user.
     *
     * <p>Called when a new reset request is issued so that the previous
     * token can no longer be redeemed; this protects against a stale
     * email link being used after the user explicitly asked for a new
     * link.</p>
     *
     * <p>Defensively annotated with {@code @Transactional} so that callers
     * outside an existing transactional context (e.g. a future scheduled
     * cleanup job or an integration test) cannot accidentally execute this
     * bulk update without a surrounding transaction.</p>
     */
    @Modifying
    @Transactional
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now "
            + "WHERE t.user.id = :userId AND t.usedAt IS NULL")
    int invalidateActiveTokensForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now "
            + "WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now")
    int consumeActiveToken(@Param("id") Long id, @Param("now") LocalDateTime now);
}
