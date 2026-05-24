package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistent record of a password-reset token issued to a {@link User}.
 *
 * <p>The presence of this row is what makes the
 * {@code POST /api/v1/auth/reset-password} endpoint succeed. A row is
 * created when a user requests {@code POST /api/v1/auth/forgot-password},
 * and is marked used (by setting {@link #usedAt}) the first time it is
 * successfully redeemed — single-use semantics are enforced by the
 * service layer checking that {@code usedAt IS NULL} before consuming.</p>
 *
 * <p><b>Defence-in-depth.</b> The database stores only a deterministic
 * hash of the reset token, never the raw token. The unhashed token only
 * exists in memory long enough to build the email link, which limits
 * the blast radius of a database leak.</p>
 *
 * <p><b>One active token per user.</b> The unique constraint on
 * {@code active_token_user_id} (a generated column populated by the
 * database to {@code user_id} when {@link #usedAt} is {@code NULL} and
 * to {@code NULL} otherwise) guarantees that requesting a new token
 * automatically invalidates any unused predecessor — eliminating the
 * "two pending reset emails" race that otherwise lets an attacker
 * abuse a stale token.</p>
 *
 * <p>{@link #expiresAt} is checked at redemption time so a token
 * leaked from email after its window closes is still useless.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_password_reset_tokens_token", columnList = "token", unique = true),
        @Index(name = "idx_password_reset_tokens_user_id", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_password_reset_tokens_active_user", columnNames = { "active_token_user_id" })
})
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "active_token_user_id", insertable = false, updatable = false)
    private Long activeTokenUserId;
}
