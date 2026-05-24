package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.PasswordResetToken;

/**
 * Persistence operations for {@link PasswordResetToken} \u2014 the
 * single-use, time-bounded token mailed to a learner who has requested
 * a password reset.
 *
 * <p>This repository is the only persistence-layer surface that the
 * password-reset security boundary depends on, so its three operations
 * are designed for defence-in-depth:</p>
 * <ul>
 *   <li>{@link #findByTokenHash(String)} looks up by the SHA-256
 *       <em>hash</em> of the emailed token (never the raw token)
 *       so a database leak does not allow attackers to redeem live
 *       reset links.</li>
 *   <li>{@link #invalidateActiveTokensForUser(Long, java.time.LocalDateTime)}
 *       runs as a single atomic UPDATE so a freshly-issued token
 *       always supersedes every stale token in one round trip.</li>
 *   <li>{@link #consumeActiveToken(Long, java.time.LocalDateTime)}
 *       is an atomic compare-and-set (UPDATE \u2026 WHERE
 *       used_at IS NULL AND expires_at &gt; now) so a token can be
 *       redeemed at most once even under concurrent requests; the
 *       caller checks the returned row count rather than re-reading
 *       the entity to detect a successful consume.</li>
 * </ul>
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Looks up a reset token by the SHA-256 hash of the raw token
     * value emailed to the learner. The raw token is never persisted,
     * so a stolen database snapshot does not expose redeemable links.
     * Backed by the unique index on {@code token_hash}.
     */
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

    /**
     * Atomically marks the given token as consumed iff it is still
     * unused and not yet expired, returning the number of rows updated
     * (0 = the token was already used, expired, or never existed; 1 =
     * caller may proceed to set the new password).
     *
     * <p>The {@code WHERE used_at IS NULL AND expires_at &gt; :now}
     * predicate makes this a compare-and-set: the database guarantees
     * that two concurrent reset attempts on the same token will produce
     * exactly one row updated and one row not updated, so the caller
     * can race-safely treat a non-zero return as "this caller is the
     * sole consumer". Re-reading the entity afterwards is unnecessary
     * and would re-introduce a TOCTOU window.</p>
     */
    @Modifying
    @Transactional
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now "
            + "WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now")
    int consumeActiveToken(@Param("id") Long id, @Param("now") LocalDateTime now);
}
