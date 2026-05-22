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
 * Persistent record of a password-reset token issued to a user.
 *
 * <p>The presence of this row is what makes the
 * {@code POST /api/v1/auth/reset-password} endpoint succeed. A row is
 * created when a user requests {@code POST /api/v1/auth/forgot-password},
 * and is marked used (and thereby invalidated) the first time it is
 * successfully redeemed.</p>
 *
 * <p>The database stores only a deterministic hash of the reset token, not
 * the raw token itself. The unhashed token only exists in memory long
 * enough to build the email link, which limits the blast radius of a
 * database leak.</p>
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
